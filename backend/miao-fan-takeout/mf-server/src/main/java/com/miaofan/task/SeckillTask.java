package com.miaofan.task;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.entity.SeckillActivity;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.service.SeckillCacheService;
import com.miaofan.service.SeckillOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抢购模块定时任务: 库存对账 + 消息重投
 */
@Slf4j
@Component
public class SeckillTask {

    /**
     * 消息闲置多久算"处理失败需要重投"
     */
    private static final Duration RETRY_MIN_IDLE = Duration.ofMinutes(5);

    /**
     * 同一条消息最多重投几次, 超过就进死信处理
     */
    private static final long MAX_RETRY_COUNT = 3L;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private SeckillOrderService seckillOrderService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 库存对账: 每 5 分钟核对一次 Redis 预扣库存与数据库库存
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void reconcileStock() {
        List<SeckillActivity> activities =
                seckillActivityMapper.listByStatus(SeckillConstant.ACTIVITY_ONLINE);

        for (SeckillActivity activity : activities) {
            Long activityId = activity.getId();

            // 活动已过结束时间: 收尾——状态置为已结束并清理 Redis
            if (activity.getEndTime().isBefore(LocalDateTime.now())) {
                seckillActivityMapper.update(SeckillActivity.builder()
                        .id(activityId)
                        .status(SeckillConstant.ACTIVITY_FINISHED)
                        .build());
                seckillCacheService.clear(activityId);
                log.info("活动已到结束时间, 置为已结束并清理缓存: activityId={}", activityId);
                continue;
            }

            int expected = activity.getTotalStock() - activity.getSoldStock();
            Integer redisStock = seckillCacheService.getStock(activityId);

            if (redisStock == null) {
                seckillCacheService.warmUp(activityId);
                log.warn("Redis 库存缺失, 已重新预热: activityId={}, 期望库存={}", activityId, expected);
            } else if (redisStock > expected) {
                // Redis 库存比数据库还多, 说明可能卖出数据库兜不住的量, 必须按数据库校正回来
                stringRedisTemplate.opsForValue()
                        .set(SeckillConstant.stockKey(activityId), String.valueOf(expected));
                log.warn("Redis 库存多于数据库, 已校正: activityId={}, redis={}, 数据库={}",
                        activityId, redisStock, expected);
            } else if (redisStock < expected) {
                // Redis 库存少于数据库是正常的: 消息可能还在消费中。
                // 这时候如果把库存补上去, 会把"已经预扣给其他用户"的名额再次放出去, 造成超卖, 所以只记录不动手
                log.info("Redis 库存少于数据库(可能有消息在处理中), 不做校正: activityId={}, redis={}, 数据库={}",
                        activityId, redisStock, expected);
            }
        }
    }

    /**
     * 重投处理失败的消息: 每分钟检查一次 pending 列表
     */
    @Scheduled(cron = "0 * * * * *")
    public void retryPendingMessages() {
        retryPendingMessages(RETRY_MIN_IDLE);
    }

    /**
     * 重投逻辑, 单独抽出来是为了能在测试里用更短的闲置时间
     *
     * @param minIdleTime 消息闲置多久才认为需要重投
     */
    public void retryPendingMessages(Duration minIdleTime) {
        try {
            // 只查当前消费者名下的 pending 消息
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream()
                    .pending(SeckillConstant.STREAM_KEY,
                            Consumer.from(SeckillConstant.STREAM_GROUP, SeckillConstant.STREAM_CONSUMER));
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            for (PendingMessage pending : pendingMessages) {
                if (pending.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) < 0) {
                    continue;
                }

                // 说明: Spring Data Redis 2.7 的 StreamOperations 没有暴露 XCLAIM,
                // 这里用 XRANGE 按消息id把内容取回来重放, 处理完再确认。
                // 因为消费逻辑是幂等的(唯一索引兜底), 所以重复投递不会产生脏数据;
                // 如果要多消费者严格排他, 可以改用底层 connection.streamCommands().xClaim(...)
                String messageId = pending.getId().getValue();
                // StringRedisTemplate 的 Stream 操作泛型是 <String, Object, Object>, 取回后需要转成字符串 Map
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                        .range(SeckillConstant.STREAM_KEY, Range.closed(messageId, messageId));
                if (records == null || records.isEmpty()) {
                    // 消息已经被裁剪掉, 直接确认, 避免一直挂在 pending 里
                    acknowledge(pending.getId());
                    continue;
                }

                boolean exhausted = pending.getTotalDeliveryCount() > MAX_RETRY_COUNT;
                for (MapRecord<String, Object, Object> record : records) {
                    try {
                        seckillOrderService.handleSeckillMessage(toStringMap(record.getValue()));
                        acknowledge(record.getId());
                        log.info("pending 消息重投成功: messageId={}", record.getId());
                    } catch (Exception e) {
                        if (exhausted) {
                            // 超过重试上限: 确认掉并告警, 避免毒消息一直堆积
                            log.error("消息重试超过 {} 次, 进入死信处理并丢弃: messageId={}, body={}",
                                    MAX_RETRY_COUNT, record.getId(), record.getValue(), e);
                            acknowledge(record.getId());
                        } else {
                            log.error("pending 消息重投仍然失败, 保留待下次重试: messageId={}", record.getId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理 pending 消息时发生异常", e);
        }
    }

    private void acknowledge(org.springframework.data.redis.connection.stream.RecordId recordId) {
        stringRedisTemplate.opsForStream()
                .acknowledge(SeckillConstant.STREAM_KEY, SeckillConstant.STREAM_GROUP, recordId);
    }

    /**
     * Stream 里取回的消息是 Object 类型的键值对, 统一转成字符串
     */
    private Map<String, String> toStringMap(Map<Object, Object> raw) {
        Map<String, String> body = new HashMap<>(raw.size());
        raw.forEach((key, value) -> body.put(String.valueOf(key), String.valueOf(value)));
        return body;
    }
}
