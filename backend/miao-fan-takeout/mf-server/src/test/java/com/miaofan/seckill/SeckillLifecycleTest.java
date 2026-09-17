package com.miaofan.seckill;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.entity.Orders;
import com.miaofan.entity.SeckillActivity;
import com.miaofan.mapper.OrderMapper;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.service.SeckillCacheService;
import com.miaofan.task.OrderTask;
import com.miaofan.task.SeckillTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抢购生命周期测试: 超时取消回补库存、库存对账、活动结束收尾、消息重投
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillLifecycleTest {

    private static final int TOTAL_STOCK = 5;

    private static final long USER_ID = 910001L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderTask orderTask;

    @Autowired
    private SeckillTask seckillTask;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long activityId;

    @BeforeEach
    void setUp() {
        SeckillActivity activity = SeckillActivity.builder()
                .name("生命周期测试活动-" + System.currentTimeMillis())
                .dishId(51L)
                .seckillPrice(new BigDecimal("9.90"))
                .totalStock(TOTAL_STOCK)
                .soldStock(0)
                .perUserLimit(1)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .status(SeckillConstant.ACTIVITY_ONLINE)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        seckillActivityMapper.insert(activity);
        activityId = activity.getId();
        seckillCacheService.warmUp(activityId);
    }

    @AfterEach
    void tearDown() {
        if (activityId == null) {
            return;
        }
        jdbcTemplate.update("delete from order_detail where order_id in "
                + "(select id from orders where seckill_activity_id = ?)", activityId);
        jdbcTemplate.update("delete from orders where seckill_activity_id = ?", activityId);
        jdbcTemplate.update("delete from seckill_record where activity_id = ?", activityId);
        jdbcTemplate.update("delete from seckill_activity where id = ?", activityId);
        seckillCacheService.clear(activityId);
        stringRedisTemplate.delete(SeckillConstant.resultKey(activityId, USER_ID));
    }

    /**
     * 抢购订单超时未支付: 订单被取消, 数据库与 Redis 库存都要还回去
     */
    @Test
    void cancelledSeckillOrderShouldRecoverStock() {
        // 模拟一次抢购成功: Redis 预扣 1 件, 数据库已售 1 件
        stringRedisTemplate.opsForValue().decrement(SeckillConstant.stockKey(activityId));
        stringRedisTemplate.opsForHash()
                .increment(SeckillConstant.userCountKey(activityId), String.valueOf(USER_ID), 1L);
        seckillActivityMapper.deductStock(activityId);

        assertEquals("4", stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId)),
                "抢购后 Redis 库存应为 4");

        // 造一条 20 分钟前下单、至今未支付的抢购订单
        Orders orders = Orders.builder()
                .number("TEST-" + System.currentTimeMillis())
                .status(Orders.PENDING_PAYMENT)
                .userId(USER_ID)
                // address_book_id 是 NOT NULL 列, 必须给值
                .addressBookId(1L)
                .seckillActivityId(activityId)
                .orderTime(LocalDateTime.now().minusMinutes(20))
                // pay_method / delivery_status / tableware_status 都是 NOT NULL 列,
                // 而 OrderMapper.insert 会显式插入这些字段, 传 null 会覆盖数据库默认值从而报错
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("9.90"))
                .deliveryStatus(1)
                .packAmount(0)
                .tablewareNumber(1)
                .tablewareStatus(1)
                .build();
        orderMapper.insert(orders);

        jdbcTemplate.update("insert into seckill_record (activity_id, user_id, order_id, status, create_time, update_time) "
                + "values (?, ?, ?, ?, now(), now())",
                activityId, USER_ID, orders.getId(), SeckillConstant.RECORD_SUCCESS);

        // 执行超时取消任务
        orderTask.processTimeoutOrder();

        assertEquals(Orders.CANCELLED,
                jdbcTemplate.queryForObject("select status from orders where id = ?", Integer.class, orders.getId()),
                "超时未支付订单应被取消");
        assertEquals(0,
                jdbcTemplate.queryForObject("select sold_stock from seckill_activity where id = ?", Integer.class, activityId),
                "数据库已售数量应回补到 0");
        assertEquals(String.valueOf(TOTAL_STOCK),
                stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId)),
                "Redis 库存应回补到总库存");
        assertNull(stringRedisTemplate.opsForHash()
                        .get(SeckillConstant.userCountKey(activityId), String.valueOf(USER_ID)),
                "用户占位应被释放(策略上允许用户重新抢购)");
        assertEquals(SeckillConstant.RECORD_FAILED,
                jdbcTemplate.queryForObject("select status from seckill_record where activity_id = ? and user_id = ?",
                        Integer.class, activityId, USER_ID),
                "抢购流水应标记为失败");
    }

    /**
     * Redis 库存比数据库还多时(可能超卖), 对账任务必须按数据库校正
     */
    @Test
    void reconcileShouldFixInflatedRedisStock() {
        // 数据库已售 2 件, 期望 Redis 库存 = 5 - 2 = 3
        seckillActivityMapper.deductStock(activityId);
        seckillActivityMapper.deductStock(activityId);
        // 人为把 Redis 库存改成 99, 模拟数据不一致
        stringRedisTemplate.opsForValue().set(SeckillConstant.stockKey(activityId), "99");

        seckillTask.reconcileStock();

        assertEquals("3", stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId)),
                "Redis 库存虚高时应按数据库校正");
    }

    /**
     * 活动过了结束时间: 状态置为已结束, 并清理 Redis 缓存
     */
    @Test
    void reconcileShouldFinishEndedActivity() {
        jdbcTemplate.update("update seckill_activity set end_time = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), activityId);

        seckillTask.reconcileStock();

        assertEquals(SeckillConstant.ACTIVITY_FINISHED,
                jdbcTemplate.queryForObject("select status from seckill_activity where id = ?", Integer.class, activityId),
                "到期的活动状态应置为已结束");
        assertNull(stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId)),
                "活动结束后缓存应被清理");
    }

    /**
     * 消息体非法的"毒消息"处理失败后不能被确认, 必须留在 pending 里等待重投
     */
    @Test
    void poisonMessageShouldStayPendingAndNotBreakRetry() throws Exception {
        Map<String, String> body = new HashMap<>(4);
        body.put("activityId", "not-a-number");
        body.put("userId", "1");
        body.put("addressBookId", "1");
        RecordId messageId = stringRedisTemplate.opsForStream().add(SeckillConstant.STREAM_KEY, body);

        // 等消费者把它读走并处理失败(消费者线程每 1 秒轮询一次)
        Thread.sleep(3000);

        PendingMessages pendingMessages = stringRedisTemplate.opsForStream().pending(
                SeckillConstant.STREAM_KEY,
                Consumer.from(SeckillConstant.STREAM_GROUP, SeckillConstant.STREAM_CONSUMER));
        boolean pending = false;
        for (PendingMessage message : pendingMessages) {
            if (message.getIdAsString().equals(messageId.getValue())) {
                pending = true;
            }
        }
        assertTrue(pending, "处理失败的消息应停留在 pending 列表中等待重试");

        // 重投一次: 消息依旧非法, 应该被捕获而不是把任务打挂
        seckillTask.retryPendingMessages(Duration.ZERO);

        // 清理这条测试消息
        stringRedisTemplate.opsForStream()
                .acknowledge(SeckillConstant.STREAM_KEY, SeckillConstant.STREAM_GROUP, messageId);
        stringRedisTemplate.opsForStream().delete(SeckillConstant.STREAM_KEY, messageId);
    }
}
