package com.example.audiobackend.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀链路的集成测试。每条测试对应一个"不这么做就会出的事故"。
 *
 * <p><b>跑法</b>（类名以 IT 结尾，所以 {@code mvn test} 默认不跑它 ——
 * 默认会跑 {@code AudiobackendApplicationTests}，那是个 @SpringBootTest，
 * 会连带启动 RabbitMQ 监听容器，和正在跑的应用抢消息）：
 *
 * <pre>
 *   1. 应用必须在跑（mvn spring-boot:run）
 *   2. mvn -q test -Dtest=SeckillIT
 * </pre>
 *
 * <p><b>为什么用专用活动 id 900 和一个专用用户</b>：测试要能随便重置库存、
 * 随便删订单，不能碰 actId=1 那份真实数据。每条测试结束都把 actId=900 的订单删干净。
 *
 * <p>用黑盒 HTTP 而不是 @SpringBootTest：测的是用户真正打的那个接口
 * （拦截器、令牌桶、Lua、MQ 全在内），而且不会多出一个消费者来抢消息。
 */
@Tag("integration")
class SeckillIT {

    private static final long ACT_ID = 900L;
    private static final long PRODUCT_ID = 1L;
    private static final int STOCK = 100;

    private static final String STOCK_KEY = "audio:seckill:stock:" + ACT_ID;
    private static final String META_KEY = "audio:seckill:meta:" + ACT_ID;
    private static final String BOUGHT_KEY = "audio:seckill:bought:" + ACT_ID;
    private static final String RATE_KEY = "audio:seckill:rate:" + ACT_ID;
    private static final String IDEM_PREFIX = "audio:seckill:idem:" + ACT_ID + ":";

    private static final String USERNAME = "it_seckill_user";
    private static final String PASSWORD = "itpass123";

    /** 压测账号（k6u_*）的密码，见 loadtest/seed-tokens.ps1 */
    private static final String K6_PASSWORD = "k6pass123";

    private Connection jdbc;
    private StringRedisTemplate redis;
    private String token;
    private long userId;

    @BeforeAll
    static void requireRunningApp() {
        Assumptions.assumeTrue(TestEnv.appIsUp(),
                "应用没在 localhost:8080 上跑，跳过集成测试（先 mvn spring-boot:run）");
    }

    @BeforeEach
    void setUp() throws Exception {
        jdbc = TestEnv.jdbc();
        redis = TestEnv.redis();
        token = login();
        userId = queryLong("SELECT id FROM user WHERE username = '" + USERNAME + "'");
        resetActivity(STOCK, 1);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (jdbc != null) {
                execute("DELETE FROM order_item WHERE order_id IN "
                        + "(SELECT id FROM orders WHERE activity_id = " + ACT_ID + ")");
                execute("DELETE FROM payment WHERE order_id IN "
                        + "(SELECT id FROM orders WHERE activity_id = " + ACT_ID + ")");
                execute("DELETE FROM orders WHERE activity_id = " + ACT_ID);
                execute("DELETE FROM seckill_activity WHERE id = " + ACT_ID);
            }
            if (redis != null) {
                redis.delete(List.of(STOCK_KEY, META_KEY, BOUGHT_KEY, RATE_KEY));
                clearIdemGates();
                ((LettuceConnectionFactory) redis.getConnectionFactory()).destroy();
            }
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @Test
    @DisplayName("同一个 requestId 提交两次：只建一张单，库存只扣一次")
    void sameRequestIdCreatesExactlyOneOrder() throws Exception {
        String requestId = "it-" + UUID.randomUUID();

        Resp first = seckill(requestId);
        assertEquals(200, first.code());
        assertEquals("排队中，请稍候", first.message());
        assertNotNull(first.orderNo(), "第一次应该拿到订单号");
        assertTrue(waitForOrder(first.orderNo(), 15), "消费者应该在 15 秒内建出订单");

        Resp second = seckill(requestId);
        assertEquals(200, second.code());

        // 先断言数据，再断言提示语。这两件事是分开的：
        // 数据（只建一张单、只扣一次库存）是正确性；提示语只是给用户看的一句话。
        // 分开断言，红了才知道坏的是哪一层。
        Thread.sleep(2000);
        assertEquals(1, countOrdersByRequestId(requestId), "同一个 requestId 只能对应一张订单");
        assertEquals(STOCK - 1, dbActivityStock(), "DB 库存只能扣一次");
        assertEquals(String.valueOf(STOCK - 1), redis.opsForValue().get(STOCK_KEY),
                "Redis 库存只能扣一次");

        // 上面三条到这里都绿 = 数据是干净的。下面这条红 = 只有"回答"是错的。
        assertTrue(second.message().contains("重复提交"),
                "第二次应该被判为重复提交，实际是：" + second.message());
        assertEquals(first.orderNo(), second.orderNo(),
                "重复提交必须把第一张的单号还回来，而不是给一个新单号");
    }

    @Test
    @DisplayName("同一个 requestId 并发提交 50 次：只建一张单，50 个请求拿到的是同一个订单号")
    void concurrentSameRequestIdCreatesExactlyOneOrder() throws Exception {
        String requestId = "it-cc-" + UUID.randomUUID();
        int threads = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        List<Resp> responses = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        // 所有线程在这里一起等，确保是"同时"打进去，而不是排队打进去
                        startLine.await();
                        responses.add(seckill(requestId));
                    } catch (Exception e) {
                        responses.add(new Resp(-1, e.toString(), null));
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startLine.countDown();
            assertTrue(finished.await(60, TimeUnit.SECONDS), "50 个请求应该在 60 秒内跑完");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads, responses.size());

        long queued = responses.stream()
                .filter(r -> "排队中，请稍候".equals(r.message()))
                .count();
        assertEquals(1, queued, "只能有一个请求真正抢到名额，实际有 " + queued + " 个");

        Thread.sleep(2000);
        assertEquals(1, countOrdersByRequestId(requestId), "并发下也只能建一张单");
        assertEquals(STOCK - 1, dbActivityStock(), "DB 库存只能扣一次");
        assertEquals(String.valueOf(STOCK - 1), redis.opsForValue().get(STOCK_KEY),
                "Redis 库存只能扣一次");

        String winnerOrderNo = responses.stream()
                .filter(r -> "排队中，请稍候".equals(r.message()))
                .findFirst().orElseThrow().orderNo();
        for (Resp r : responses) {
            assertNotNull(r.orderNo(), "每个请求都该拿到单号，实际：" + r.message());
            assertEquals(winnerOrderNo, r.orderNo(),
                    "没抢到的那些必须还回同一个单号，而不是给新单号或 null");
        }
    }

    @Test
    @DisplayName("Redis 幂等门丢了之后重试：撞 uk_user_request，名额必须退回（不能少卖一件）")
    void lostGateRetryHitsUserRequestIndexAndCompensates() throws Exception {
        // 限购 2：否则第二次请求会先被限购挡住，根本走不到消费者，
        // 也就测不到 uk_user_request 这条路径
        resetActivity(STOCK, 2);
        String requestId = "it-uk-" + UUID.randomUUID();

        Resp first = seckill(requestId);
        assertEquals("排队中，请稍候", first.message());
        assertTrue(waitForOrder(first.orderNo(), 15), "第一张单应该建出来");

        // 手工制造「门丢了」：删掉幂等门 + 把已购数清零，
        // 等价于 Redis 整库丢失或门过了 1 天 TTL 的效果
        redis.delete(IDEM_PREFIX + userId + ":" + requestId);
        redis.opsForHash().delete(BOUGHT_KEY, String.valueOf(userId));

        Resp second = seckill(requestId);
        assertEquals("排队中，请稍候", second.message(), "门没了，这次会被放行并发到 MQ");
        assertNotEquals(first.orderNo(), second.orderNo(), "这次会生成一个新单号");

        // 消费者会：扣 DB 库存 → 插订单 → 撞 uk_user_request → 整个事务回滚 → 补偿 Redis
        Thread.sleep(4000);

        assertEquals(1, countOrdersByRequestId(requestId), "库里最终还是只有第一张单");
        assertEquals(STOCK - 1, dbActivityStock(), "DB 库存只被第一张单扣掉，第二张已回滚");
        assertEquals(String.valueOf(STOCK - 1), redis.opsForValue().get(STOCK_KEY),
                "Redis 名额必须退回去，否则就永久少卖一件");
    }

    @Test
    @DisplayName("库存 100、200 个不同用户同时抢：恰好 100 张单，不超卖也不漏卖")
    void noOversellUnderConcurrentLoad() throws Exception {
        int users = 200;

        // 现登 200 个账号拿 token。为什么不在数据库里伪造用户：
        // 鉴权要读 Redis 里的 token，伪造用户还得连带伪造 token，测出来的就不是真实链路了。
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= users; i++) {
            tokens.add(loginAs("k6u_" + i));
        }
        resetActivity(STOCK, 1);

        // 固定 50 个线程跑 200 个任务：Windows 上 200 个瞬时新连接会撞 listen backlog，
        // 连接被 RST 会让"少卖"和"超卖"混在一起，测不清到底是谁的问题
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch finished = new CountDownLatch(users);
        List<Resp> responses = Collections.synchronizedList(new ArrayList<>());
        try {
            for (String bearer : tokens) {
                pool.submit(() -> {
                    try {
                        responses.add(seckillWith(bearer, "it-os-" + UUID.randomUUID()));
                    } catch (Exception e) {
                        responses.add(new Resp(-1, e.toString(), null));
                    } finally {
                        finished.countDown();
                    }
                });
            }
            assertTrue(finished.await(120, TimeUnit.SECONDS), "200 个请求应该在 120 秒内跑完");
        } finally {
            pool.shutdownNow();
        }

        long won = responses.stream().filter(r -> "排队中，请稍候".equals(r.message())).count();
        long soldOut = responses.stream().filter(r -> "已售罄".equals(r.message())).count();
        long unexpected = responses.size() - won - soldOut;
        assertEquals(0, unexpected, "不该出现第三种回答：" + sampleUnexpected(responses));
        assertEquals(STOCK, won, "放行人数必须等号于库存，多了是超卖，少了是漏卖");

        // 消费者是异步的，等订单补满
        assertTrue(waitForOrderCount(STOCK, 60), "100 张单应该都建出来，实际 " + countOrders());
        assertEquals(STOCK, countOrders(), "订单数必须等于库存数");
        assertEquals(0, dbActivityStock(), "DB 库存必须刚好扣到 0");
        assertEquals("0", redis.opsForValue().get(STOCK_KEY), "Redis 库存必须刚好扣到 0");
    }

    // ---------------------------------------------------------------- 辅助

    private record Resp(int code, String message, String orderNo) {
    }

    private Resp seckill(String requestId) throws Exception {
        return seckillWith(token, requestId);
    }

    private Resp seckillWith(String bearer, String requestId) throws Exception {
        String path = "/api/order/seckill/" + ACT_ID + "?requestId=" + requestId;
        HttpResponse<String> res = TestEnv.http(TestEnv.post(path, bearer, null));
        JsonNode node = TestEnv.json(res.body());
        return new Resp(
                node.path("code").asInt(),
                node.path("data").path("message").asText(""),
                node.path("data").path("orderNo").asText(null));
    }

    /** 登录一个已存在的账号。压测账号由 seed-tokens.ps1 批量注册过 */
    private String loginAs(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + K6_PASSWORD + "\"}";
        JsonNode node = TestEnv.json(TestEnv.http(TestEnv.post("/api/user/login", null, body)).body());
        String t = node.path("data").path("token").asText(null);
        assertNotNull(t, "登录失败：" + username + "，响应：" + node);
        return t;
    }

    private int countOrders() throws Exception {
        return (int) queryLong("SELECT COUNT(*) FROM orders WHERE activity_id = " + ACT_ID);
    }

    private boolean waitForOrderCount(int expected, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (countOrders() >= expected) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    private String sampleUnexpected(List<Resp> responses) {
        return responses.stream()
                .filter(r -> !"排队中，请稍候".equals(r.message()) && !"已售罄".equals(r.message()))
                .limit(3)
                .map(Resp::message)
                .collect(java.util.stream.Collectors.toList())
                .toString();
    }

    private String login() throws Exception {
        String body = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
        // 已注册过就返回 4xx，忽略即可 —— 这条测试不关心注册
        TestEnv.http(TestEnv.post("/api/user/register", null,
                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD
                        + "\",\"nickname\":\"it\"}"));
        JsonNode node = TestEnv.json(TestEnv.http(TestEnv.post("/api/user/login", null, body)).body());
        String t = node.path("data").path("token").asText(null);
        assertNotNull(t, "登录没拿到 token，响应：" + node);
        return t;
    }

    /**
     * 重置活动到指定库存。三处都要写：DB 活动行、Redis 库存、Redis 元信息。
     * 少写一处就会得到"看起来对但测的不是想测的东西"。
     */
    private void resetActivity(int stock, int limitPerUser) throws Exception {
        execute("DELETE FROM seckill_activity WHERE id = " + ACT_ID);
        execute("INSERT INTO seckill_activity "
                + "(id, product_id, seckill_price, stock, limit_per_user, start_time, end_time, status) "
                + "VALUES (" + ACT_ID + ", " + PRODUCT_ID + ", 499.00, " + stock + ", " + limitPerUser
                + ", NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 1 HOUR, 1)");

        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(STOCK_KEY, String.valueOf(stock));
        redis.opsForHash().putAll(META_KEY, Map.of(
                "startTime", String.valueOf(now - 3600),
                "endTime", String.valueOf(now + 3600),
                "limitPerUser", String.valueOf(limitPerUser),
                "productId", String.valueOf(PRODUCT_ID),
                "seckillPrice", "499.00"));
        redis.delete(List.of(BOUGHT_KEY, RATE_KEY));
        clearIdemGates();
    }

    private void clearIdemGates() {
        Set<String> keys = redis.keys(IDEM_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(new ArrayList<>(keys));
        }
    }

    private void execute(String sql) throws Exception {
        try (Statement st = jdbc.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private int countOrdersByRequestId(String requestId) throws Exception {
        try (Statement st = jdbc.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM orders WHERE request_id = '" + requestId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int dbActivityStock() throws Exception {
        try (Statement st = jdbc.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT stock FROM seckill_activity WHERE id = " + ACT_ID)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private long queryLong(String sql) throws Exception {
        try (Statement st = jdbc.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查不到数据：" + sql);
            return rs.getLong(1);
        }
    }

    /** 消费者是异步的，只能轮询等；等不到就是真的没建出来，不要用 sleep 猜时间 */
    private boolean waitForOrder(String orderNo, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try (Statement st = jdbc.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM orders WHERE order_no = '" + orderNo + "'")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return true;
                }
            }
            Thread.sleep(200);
        }
        return false;
    }
}
