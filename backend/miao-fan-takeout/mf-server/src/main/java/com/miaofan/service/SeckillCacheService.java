package com.miaofan.service;

import com.alibaba.fastjson.JSON;
import com.miaofan.constant.SeckillConstant;
import com.miaofan.entity.SeckillRecord;
import com.miaofan.enumeration.SeckillResult;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.mapper.SeckillRecordMapper;
import com.miaofan.vo.SeckillActivityVO;
import com.miaofan.vo.SeckillResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 抢购活动缓存服务
 * <p>
 * 统一负责 Redis 里"活动详情"与"预扣库存"的读写, 业务代码不直接碰 key
 * <p>
 * 为什么用 StringRedisTemplate + JSON 而不是默认的 RedisTemplate:
 * 默认 RedisTemplate 的值序列化器是 JDK 序列化, 存进去的内容在 redis-cli 里是一串乱码,
 * 排查问题非常痛苦。用 String + JSON 存, 值是可读的, 调试成本低。
 */
@Slf4j
@Service
public class SeckillCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillRecordMapper seckillRecordMapper;

    /**
     * 预热: 活动上架时把详情与剩余库存写入 Redis
     * <p>
     * 剩余库存 = 总库存 - 已售数量
     */
    public void warmUp(Long activityId) {
        SeckillActivityVO activity = seckillActivityMapper.getById(activityId);
        if (activity == null) {
            log.warn("活动预热失败, 活动不存在: {}", activityId);
            return;
        }

        int remainStock = activity.getTotalStock() - activity.getSoldStock();
        String stockKey = SeckillConstant.stockKey(activityId);
        String userCountKey = SeckillConstant.userCountKey(activityId);

        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(remainStock));
        stringRedisTemplate.opsForValue()
                .set(SeckillConstant.activityCacheKey(activityId), JSON.toJSONString(activity));

        // 重建"一人一单"占位: Redis 被清空或服务重启后, 从数据库流水里恢复已购记录,
        // 否则用户在 Redis 里又变成"没买过", 会出现重复抢购
        stringRedisTemplate.delete(userCountKey);
        List<SeckillRecord> records = new ArrayList<>();
        records.addAll(seckillRecordMapper.listByActivityIdAndStatus(activityId, SeckillConstant.RECORD_QUEUING));
        records.addAll(seckillRecordMapper.listByActivityIdAndStatus(activityId, SeckillConstant.RECORD_SUCCESS));
        for (SeckillRecord record : records) {
            stringRedisTemplate.opsForHash().increment(userCountKey, String.valueOf(record.getUserId()), 1L);
        }

        // 活动结束后这些 key 就没有意义了, 设置过期时间避免长期占用内存
        long ttlSeconds = Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds() + 3600;
        if (ttlSeconds > 0) {
            stringRedisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.expire(userCountKey, ttlSeconds, TimeUnit.SECONDS);
        }

        log.info("活动预热完成: id={}, 剩余库存={}, 已购用户数={}", activityId, remainStock, records.size());
    }

    /**
     * 清理活动缓存: 停售或活动结束后调用
     */
    public void clear(Long activityId) {
        stringRedisTemplate.delete(Arrays.asList(
                SeckillConstant.activityCacheKey(activityId),
                SeckillConstant.stockKey(activityId),
                SeckillConstant.userCountKey(activityId)));
        log.info("活动缓存已清理: id={}", activityId);
    }

    /**
     * 活动被修改后刷新缓存, 保证缓存与数据库一致
     * <p>
     * 这里没有采用"删除缓存等下次查询再加载"的懒加载, 而是直接重建,
     * 原因是抢购场景下库存必须提前就绪, 不能等到第一个用户进来才初始化
     */
    public void refresh(Long activityId) {
        SeckillActivityVO activity = seckillActivityMapper.getById(activityId);
        if (activity != null
                && SeckillConstant.ACTIVITY_ONLINE.equals(activity.getStatus())
                && activity.getEndTime().isAfter(LocalDateTime.now())) {
            warmUp(activityId);
        } else {
            clear(activityId);
        }
    }

    /**
     * 查询活动详情, 缓存优先
     */
    public SeckillActivityVO getActivity(Long activityId) {
        String cacheKey = SeckillConstant.activityCacheKey(activityId);
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(json)) {
            return JSON.parseObject(json, SeckillActivityVO.class);
        }

        SeckillActivityVO activity = seckillActivityMapper.getById(activityId);
        if (activity != null) {
            stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(activity), 10, TimeUnit.MINUTES);
        }
        return activity;
    }

    /**
     * 查询 Redis 中的剩余库存, 未预热时返回 null
     */
    public Integer getStock(Long activityId) {
        String value = stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId));
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * 回补预扣库存: 下单失败或订单超时取消时调用
     * <p>
     * 只有库存 key 还在时才回补, 避免把已经清理掉的活动又写回 Redis
     */
    public void recoverStock(Long activityId, Long userId) {
        String stockKey = SeckillConstant.stockKey(activityId);
        String userCountKey = SeckillConstant.userCountKey(activityId);

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().increment(stockKey);
        }

        // 已购数量减一, 减到 0 就把这个用户的占位删掉, 允许其重新抢购
        Long remainCount = stringRedisTemplate.opsForHash()
                .increment(userCountKey, String.valueOf(userId), -1L);
        if (remainCount != null && remainCount <= 0) {
            stringRedisTemplate.opsForHash().delete(userCountKey, String.valueOf(userId));
        }
    }

    /**
     * 写入抢购结果, 供前端轮询
     */
    public void saveResult(Long activityId, Long userId, SeckillResult result, Long orderId) {
        SeckillResultVO resultVO = SeckillResultVO.builder()
                .code(result.getCode())
                .message(result.getMessage())
                .orderId(orderId)
                .build();
        stringRedisTemplate.opsForValue().set(SeckillConstant.resultKey(activityId, userId),
                JSON.toJSONString(resultVO), SeckillConstant.RESULT_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 读取抢购结果
     */
    public SeckillResultVO getResult(Long activityId, Long userId) {
        String json = stringRedisTemplate.opsForValue().get(SeckillConstant.resultKey(activityId, userId));
        return StringUtils.hasText(json) ? JSON.parseObject(json, SeckillResultVO.class) : null;
    }
}
