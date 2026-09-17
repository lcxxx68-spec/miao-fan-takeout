package com.miaofan.mq;

import com.miaofan.constant.SeckillConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 抢购下单消息生产者
 * <p>
 * 只做一件事: 把下单任务投递到 Redis Stream, 具体落库交给消费者
 */
@Component
@Slf4j
public class SeckillMessageProducer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 投递一条抢购下单消息
     *
     * @param activityId    活动id
     * @param userId        用户id
     * @param addressBookId 收货地址id
     * @return 消息id, 可用于排查问题
     */
    public RecordId send(Long activityId, Long userId, Long addressBookId) {
        Map<String, String> body = new HashMap<>(4);
        body.put("activityId", String.valueOf(activityId));
        body.put("userId", String.valueOf(userId));
        body.put("addressBookId", String.valueOf(addressBookId));

        RecordId recordId = stringRedisTemplate.opsForStream().add(SeckillConstant.STREAM_KEY, body);

        // 消息流不会自动清理, 不加限制会无限增长。
        // 这里做近似裁剪, 只保留最近 10000 条:
        // 上限远大于并发量, 正常情况不会裁掉还没消费的消息
        stringRedisTemplate.opsForStream().trim(SeckillConstant.STREAM_KEY, 10000, true);

        log.info("抢购下单消息已投递: messageId={}, activityId={}, userId={}", recordId, activityId, userId);
        return recordId;
    }
}
