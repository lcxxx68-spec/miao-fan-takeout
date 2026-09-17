package com.miaofan.mq;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.service.SeckillOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抢购下单消息消费者
 * <p>
 * 这里是"至少一次消费"的写法: 处理成功才 XACK,
 * 处理失败的消息会留在 pending 列表里等待重投, 不会凭空消失
 */
@Component
@Slf4j
public class SeckillOrderConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    @Autowired
    private SeckillOrderService seckillOrderService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        try {
            seckillOrderService.handleSeckillMessage(body);

            //处理成功, 手动确认
            stringRedisTemplate.opsForStream()
                    .acknowledge(SeckillConstant.STREAM_KEY, SeckillConstant.STREAM_GROUP, message.getId());
        } catch (Exception e) {
            //不确认, 消息留在 pending 里, 由重试任务或人工介入处理
            log.error("抢购下单消息处理失败, 等待重试: messageId={}, body={}", message.getId(), body, e);
        }
    }
}
