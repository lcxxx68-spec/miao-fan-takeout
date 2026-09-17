package com.miaofan.config;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.mq.SeckillOrderConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

/**
 * Redis Stream 异步下单配置
 * <p>
 * 用 Redis Stream 代替 RocketMQ: 抢购接口只负责把"下单请求"投递到消息流里,
 * 落库动作由消费者慢慢做, 从而把突发流量挡在数据库之外
 */
@Slf4j
@Configuration
public class RedisStreamConfig {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消费者容器: Spring 启动时自动开始消费, 关闭时自动停止
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> seckillStreamContainer(
            SeckillOrderConsumer seckillOrderConsumer) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        //没有新消息时阻塞 1 秒, 避免空轮询打满 CPU
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        createGroupIfAbsent();

        // receive 而不是 receiveAutoAck: 处理成功后才手动确认,
        // 处理失败的消息会留在 pending 列表里, 便于后续重试, 避免消息丢失
        container.receive(
                Consumer.from(SeckillConstant.STREAM_GROUP, SeckillConstant.STREAM_CONSUMER),
                StreamOffset.create(SeckillConstant.STREAM_KEY, ReadOffset.lastConsumed()),
                seckillOrderConsumer);

        return container;
    }

    /**
     * 创建消费组, 已存在时忽略异常
     * <p>
     * XGROUP CREATE 带 MKSTREAM 选项时, 消息流不存在会自动创建
     */
    private void createGroupIfAbsent() {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(SeckillConstant.STREAM_KEY, SeckillConstant.STREAM_GROUP);
            log.info("已创建 Stream 消费组: {}", SeckillConstant.STREAM_GROUP);
        } catch (Exception e) {
            log.info("Stream 消费组已存在, 跳过创建: {}", SeckillConstant.STREAM_GROUP);
        }
    }
}
