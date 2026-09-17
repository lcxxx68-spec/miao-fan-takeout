package com.miaofan.seckill;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.constant.JwtClaimsConstant;
import com.miaofan.dto.SeckillDTO;
import com.miaofan.entity.SeckillActivity;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.properties.JwtProperties;
import com.miaofan.service.SeckillCacheService;
import com.miaofan.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 抢购并发测试: 用真实 HTTP 请求并发打同一个活动, 验证不会超卖
 * <p>
 * 场景: 40 个不同用户抢 10 件库存
 * 期望: 放行数 = 数据库已售数 = 生成订单数 = 10, Redis 剩余库存 = 0, 且每个用户最多一单
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillConcurrencyTest {

    private static final int TOTAL_STOCK = 10;

    private static final int USER_COUNT = 40;

    private static final long USER_ID_BASE = 900000L;

    private static final String TEST_ADDRESS_PHONE = "13800000000";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long activityId;

    private Long addressBookId;

    private boolean addressCreated;

    @BeforeEach
    void setUp() {
        // 造一个只属于本次测试的活动: 总库存 10, 每人限购 1
        SeckillActivity activity = SeckillActivity.builder()
                .name("并发测试活动-" + System.currentTimeMillis())
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

        // 预热库存到 Redis
        seckillCacheService.warmUp(activityId);

        // 复用一条已有地址; 一条都没有时造一条临时地址
        List<Long> addressIds = jdbcTemplate.queryForList("select id from address_book limit 1", Long.class);
        if (addressIds.isEmpty()) {
            jdbcTemplate.update("insert into address_book (user_id, consignee, phone, sex, detail, label, is_default) "
                            + "values (?, ?, ?, ?, ?, ?, ?)",
                    USER_ID_BASE, "并发测试", TEST_ADDRESS_PHONE, "1", "测试地址", "公司", 1);
            addressBookId = jdbcTemplate.queryForObject(
                    "select id from address_book where phone = ? order by id desc limit 1",
                    Long.class, TEST_ADDRESS_PHONE);
            addressCreated = true;
        } else {
            addressBookId = addressIds.get(0);
        }
    }

    @AfterEach
    void tearDown() {
        if (activityId != null) {
            jdbcTemplate.update("delete from order_detail where order_id in "
                    + "(select id from orders where seckill_activity_id = ?)", activityId);
            jdbcTemplate.update("delete from orders where seckill_activity_id = ?", activityId);
            jdbcTemplate.update("delete from seckill_record where activity_id = ?", activityId);
            jdbcTemplate.update("delete from seckill_activity where id = ?", activityId);
            seckillCacheService.clear(activityId);
            for (int i = 0; i < USER_COUNT; i++) {
                stringRedisTemplate.delete(SeckillConstant.resultKey(activityId, USER_ID_BASE + i));
            }
        }
        if (addressCreated && addressBookId != null) {
            jdbcTemplate.update("delete from address_book where id = ?", addressBookId);
        }
    }

    @Test
    void concurrentSeckillShouldNotOversell() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch ready = new CountDownLatch(USER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(USER_COUNT);

        for (int i = 0; i < USER_COUNT; i++) {
            long userId = USER_ID_BASE + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                // 所有线程在这里等同一个信号, 保证尽可能同时发出请求
                start.await();
                return doSeckill(userId);
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        long begin = System.currentTimeMillis();
        start.countDown();

        int accepted = 0;
        for (Future<Integer> future : futures) {
            if (future.get(30, TimeUnit.SECONDS) == 1) {
                accepted++;
            }
        }
        long cost = System.currentTimeMillis() - begin;
        pool.shutdown();

        // 等待异步下单消费完成
        waitForConsumer(accepted);

        Integer soldStock = jdbcTemplate.queryForObject(
                "select sold_stock from seckill_activity where id = ?", Integer.class, activityId);
        Integer orderCount = jdbcTemplate.queryForObject(
                "select count(*) from orders where seckill_activity_id = ?", Integer.class, activityId);
        String redisStock = stringRedisTemplate.opsForValue().get(SeckillConstant.stockKey(activityId));
        Integer maxPerUser = jdbcTemplate.queryForObject(
                "select ifnull(max(c), 0) from (select count(*) c from seckill_record where activity_id = ? group by user_id) t",
                Integer.class, activityId);

        // 用 ASCII 输出汇总, 避免不同终端编码把中文变成乱码导致看不到数据
        System.out.printf("CONCURRENCY-TEST users=%d stock=%d | luaAccepted=%d dbSold=%d orders=%d redisStock=%s maxPerUser=%d | cost=%dms%n",
                USER_COUNT, TOTAL_STOCK, accepted, soldStock, orderCount, redisStock, maxPerUser, cost);

        assertEquals(TOTAL_STOCK, accepted, "Lua 放行的请求数必须等于总库存, 放多了就会超卖");
        assertEquals(TOTAL_STOCK, soldStock, "数据库已售数量必须等于总库存");
        assertEquals(TOTAL_STOCK, orderCount, "生成的订单数必须等于总库存");
        assertEquals("0", redisStock, "Redis 预扣库存应刚好扣完");
        assertEquals(1, maxPerUser, "每个用户最多只能抢到一单");
    }

    /**
     * 以指定用户身份发起一次抢购, 返回 1 表示被 Lua 放行(进入排队)
     */
    private int doSeckill(long userId) {
        // 注意: JwtUtil 内部会往 claims 里放过期时间, 必须传可变 Map,
        // 传 Collections.singletonMap 这类不可变集合会抛 UnsupportedOperationException
        Map<String, Object> claims = new HashMap<>(2);
        claims.put(JwtClaimsConstant.USER_ID, userId);

        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        HttpHeaders headers = new HttpHeaders();
        headers.set(jwtProperties.getUserTokenName(), token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        SeckillDTO seckillDTO = new SeckillDTO();
        seckillDTO.setAddressBookId(addressBookId);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/user/seckill/" + activityId,
                HttpMethod.POST,
                new HttpEntity<>(seckillDTO, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        Map<String, Object> body = response.getBody();
        if (body == null || !(body.get("data") instanceof Map)) {
            return 0;
        }
        Object code = ((Map<?, ?>) body.get("data")).get("code");
        // QUEUING 的 code 是 6, 表示库存扣减成功且消息已投递
        return code instanceof Number && ((Number) code).intValue() == 6 ? 1 : 0;
    }

    /**
     * 轮询等待消费者把消息处理完
     */
    private void waitForConsumer(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            Integer finished = jdbcTemplate.queryForObject(
                    "select count(*) from seckill_record where activity_id = ? and status <> 0",
                    Integer.class, activityId);
            if (finished != null && finished >= expected) {
                return;
            }
            Thread.sleep(200);
        }
        fail("等待异步下单超时, 消费者可能没有正常工作");
    }
}
