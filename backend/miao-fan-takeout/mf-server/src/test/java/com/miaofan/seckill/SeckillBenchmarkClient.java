package com.miaofan.seckill;

import com.miaofan.utils.JwtUtil;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抢购压测客户端
 * <p>
 * 刻意写成"纯客户端": 不加载 Spring 容器, 只通过 HTTP 打一个已经在独立进程里跑起来的应用。
 * 原因: 如果把压测客户端和被压测的服务放在同一个 JVM 里, 两者会互相抢 CPU,
 * 测出来的延迟没有参考价值, 也看不出"同步落库"与"异步削峰"的差别。
 * <p>
 * 运行方式(需要应用已启动在 18080):
 * <pre>
 *   mvn test -Dtest=SeckillBenchmarkClient -Dbench.url=http://localhost:18080 -Dbench.mode=async
 * </pre>
 */
class SeckillBenchmarkClient {

    /**
     * 用户端鉴权请求头名称, 与管理端(token)不同
     */
    private static final String USER_TOKEN_HEADER = "authentication";

    private static final int USERS = 200;

    private static final long USER_ID_BASE = 930000L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String baseUrl = System.getProperty("bench.url", "http://localhost:18080");

    private final String modeLabel = System.getProperty("bench.mode", "unknown");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 压测活动名称, 用于精确轮询本次活动(同一个菜品可能有多个历史活动)
     */
    private String activityName;

    /**
     * 压测用收货地址与创建它的用户令牌, 用于跑完后清理
     */
    private String addressBookId;

    private String benchUserToken;

    @Test
    void runBenchmark() throws Exception {
        String adminToken = adminLogin();
        addressBookId = createAddressBook();
        Long activityId = createActivity(adminToken);

        try {
            startActivity(adminToken, activityId);
            waitUntilStockReady(adminToken, activityId);

            List<Long> latencies = new CopyOnWriteArrayList<>();
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger abnormal = new AtomicInteger();

            ExecutorService pool = Executors.newFixedThreadPool(USERS);
            CountDownLatch ready = new CountDownLatch(USERS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(USERS);

            for (int i = 0; i < USERS; i++) {
                long userId = USER_ID_BASE + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    long begin = System.nanoTime();
                    int code = seckill(userId, activityId, addressBookId);
                    latencies.add((System.nanoTime() - begin) / 1_000_000);
                    if (code == 6) {
                        accepted.incrementAndGet();
                    } else {
                        abnormal.incrementAndGet();
                    }
                }));
            }

            ready.await(30, TimeUnit.SECONDS);
            long wallBegin = System.nanoTime();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(180, TimeUnit.SECONDS);
            }
            long wallMs = (System.nanoTime() - wallBegin) / 1_000_000;
            pool.shutdown();

            long settleMs = waitUntilAllOrdersSettled(adminToken, activityId);

            long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            double qps = wallMs == 0 ? 0 : USERS * 1000.0 / wallMs;

            System.out.printf("BENCH mode=%s users=%d | accepted=%d abnormal=%d | wall=%dms qps=%.1f "
                            + "| avg=%dms p50=%dms p90=%dms p99=%dms max=%dms | settle=%dms | activityId=%d%n",
                    modeLabel, USERS, accepted.get(), abnormal.get(), wallMs, qps,
                    avg(sorted), percentile(sorted, 50), percentile(sorted, 90),
                    percentile(sorted, 99), sorted[sorted.length - 1], settleMs, activityId);

            assertEquals(USERS, accepted.get(), "压测中应全部抢购成功");
            assertTrue(settleMs >= 0, "订单应在超时时间内全部落库完成");
        } finally {
            // 停售压测活动, 保证它不会继续影响演示环境
            try {
                post("/admin/seckill/status/0?id=" + activityId, null, adminToken);
            } catch (Exception ignored) {
                // 清理失败不影响压测结论
            }
            try {
                delete("/user/addressBook?id=" + addressBookId, benchUserToken, USER_TOKEN_HEADER);
            } catch (Exception ignored) {
                // 清理失败不影响压测结论
            }
        }
    }

    private String adminLogin() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"123456\"}";
        String response = post("/admin/employee/login", body, null);
        return extract(response, "\"token\":\"([^\"]+)\"");
    }

    /**
     * 先通过用户端接口造一条真实收货地址, 否则下单时地址校验过不去
     * <p>
     * 注意: 不能写死 addressBookId, 数据库自增主键往往已经不是从 1 开始
     */
    private String createAddressBook() throws Exception {
        long userId = USER_ID_BASE + 1;
        String userToken = JwtUtil.createJWT("miaofan-user", 7_200_000L, userClaims(userId));

        String body = "{\"consignee\":\"压测用户\",\"phone\":\"13900000000\",\"sex\":\"1\","
                + "\"provinceName\":\"北京市\",\"cityName\":\"北京市\",\"districtName\":\"东城区\","
                + "\"detail\":\"压测地址(可删除)\",\"label\":\"公司\",\"isDefault\":1}";
        // 用户端令牌的请求头名是 authentication, 管理端才是 token
        post("/user/addressBook", body, userToken, USER_TOKEN_HEADER);

        String response = get("/user/addressBook/list", userToken, USER_TOKEN_HEADER);
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("未能创建压测收货地址: " + response);
        }
        benchUserToken = userToken;
        return matcher.group(1);
    }

    /**
     * 构造 JWT 的 claims。注意必须用可变 Map:
     * JwtUtil 内部会往这个 Map 里写过期时间, 传不可变集合会抛 UnsupportedOperationException
     */
    private Map<String, Object> userClaims(long userId) {
        Map<String, Object> claims = new HashMap<>(2);
        claims.put("userId", userId);
        return claims;
    }

    private Long createActivity(String adminToken) throws Exception {
        activityName = "压测活动-" + modeLabel + "-" + System.currentTimeMillis();
        String body = String.format("{\"name\":\"%s\",\"dishId\":51,\"seckillPrice\":9.90,\"totalStock\":%d,"
                        + "\"perUserLimit\":1,\"startTime\":\"%s\",\"endTime\":\"%s\",\"status\":0}",
                activityName, USERS,
                LocalDateTime.now().minusHours(1).format(TIME_FORMAT),
                LocalDateTime.now().plusHours(2).format(TIME_FORMAT));
        post("/admin/seckill", body, adminToken);

        String page = get("/admin/seckill/page?page=1&pageSize=1&name=" + encode(activityName), adminToken);
        return Long.valueOf(extract(page, "\"id\":(\\d+)"));
    }

    private void startActivity(String adminToken, Long activityId) throws Exception {
        post("/admin/seckill/status/1?id=" + activityId, null, adminToken);
    }

    private void waitUntilStockReady(String adminToken, Long activityId) throws Exception {
        for (int i = 0; i < 50; i++) {
            String page = get("/admin/seckill/page?page=1&pageSize=1&name=" + encode(activityName), adminToken);
            if (page.contains("\"remainStock\":" + USERS)) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private int seckill(long userId, Long activityId, String addressBookId) {
        Map<String, Object> claims = new HashMap<>(2);
        claims.put("userId", userId);
        String userToken = JwtUtil.createJWT("miaofan-user", 7_200_000L, claims);

        try {
            String body = "{\"addressBookId\":" + addressBookId + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/user/seckill/" + activityId))
                    .header("authentication", userToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 注意: 响应外层也有 code(1=接口调用成功), 这里要取 data 里的抢购结果码
            Matcher matcher = Pattern.compile("\"data\":\\{\"code\":(\\d+)").matcher(response.body());
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 轮询等待全部订单落库, 返回耗时(毫秒)
     */
    private long waitUntilAllOrdersSettled(String adminToken, Long activityId) throws Exception {
        long begin = System.currentTimeMillis();
        while (System.currentTimeMillis() - begin < 120_000) {
            String page = get("/admin/seckill/page?page=1&pageSize=1&name=" + encode(activityName), adminToken);
            Matcher matcher = Pattern.compile("\"soldStock\":(\\d+)").matcher(page);
            if (matcher.find() && Integer.parseInt(matcher.group(1)) >= USERS) {
                return System.currentTimeMillis() - begin;
            }
            Thread.sleep(20);
        }
        return -1;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String post(String path, String body, String token) throws Exception {
        return post(path, body, token, null);
    }

    private String post(String path, String body, String token, String headerName) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header(headerName == null ? "token" : headerName, token);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String get(String path, String token) throws Exception {
        return get(path, token, null);
    }

    private String get(String path, String token, String headerName) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (token != null) {
            builder.header(headerName == null ? "token" : headerName, token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private String delete(String path, String token, String headerName) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .DELETE();
        if (token != null) {
            builder.header(headerName == null ? "token" : headerName, token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private String extract(String source, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("接口返回中未匹配到内容: " + regex + " -> " + source);
        }
        return matcher.group(1);
    }

    private long avg(long[] sorted) {
        long sum = 0;
        for (long value : sorted) {
            sum += value;
        }
        return sorted.length == 0 ? 0 : sum / sorted.length;
    }

    private long percentile(long[] sorted, int percent) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
