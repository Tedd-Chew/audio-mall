package com.example.audiobackend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 下单接口压测。
 * 直接在 IDEA 里右键 Run，不用 Maven。
 *
 * 用法：
 *   1. 先登录拿 token，填到 TOKEN
 *   2. 跑 sql/03-loadtest-data.sql 造数据
 *   3. 改 CONCURRENCY / REQUESTS_PER_THREAD / SAME_PRODUCT
 *   4. Run
 */
public class LoadTest {

    // ========== 改这里 ==========
    private static final String BASE_URL = "http://localhost:8080";

    /** 登录接口返回的 token，粘过来 */
    private static final String TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ6aGFuZ3NhbiIsInVzZXJJZCI6Miwicm9sZSI6IkJVWUVSIiwiaWF0IjoxNzkyNTY4NDE4LCJleHAiOjE3OTI1NzU2MTh9.dKqzJKZOTXi00-5FDDyvzhvurK4Ki62_EF4446vuGRELhB4FAMzJ1wCrzDd04kBP";

    /** 并发线程数 */
    private static final int CONCURRENCY = 200;

    /** 每个线程发几次 */
    private static final int REQUESTS_PER_THREAD = 10;

    /**
     * true  = 所有请求打同一个商品（测热点行锁竞争）
     * false = 请求分散到 100 个商品（测整体吞吐）
     */
    private static final boolean SAME_PRODUCT = false;

    /** 压测商品 id 范围：4 ~ 103（由 03-loadtest-data.sql 生成） */
    private static final long PRODUCT_ID_START = 4;
    private static final int PRODUCT_COUNT = 100;

    public static void main(String[] args) throws Exception {
        if (TOKEN.startsWith("填")) {
            System.err.println("请先登录拿 token，填到 TOKEN 常量里");
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Queue<Long> latencies = new ConcurrentLinkedQueue<>();

        Thread[] threads = new Thread[CONCURRENCY];
        for (int i = 0; i < CONCURRENCY; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();          // 等发令枪
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long productId = pickProduct(threadIndex, j);

                        long t0 = System.nanoTime();
                        HttpResponse<String> resp = client.send(
                                buildRequest(productId), HttpResponse.BodyHandlers.ofString());
                        latencies.add((System.nanoTime() - t0) / 1_000_000);

                        String body = resp.body();
                        if (resp.statusCode() == 200 && body.contains("\"code\":200")) {
                            success.incrementAndGet();
                        } else if (body.contains("库存不足")) {
                            outOfStock.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        long begin = System.currentTimeMillis();
        start.countDown();      // 发令枪
        done.await();           // 等全部跑完
        long costMs = System.currentTimeMillis() - begin;

        report(costMs, success.get(), outOfStock.get(), failed.get(), new ArrayList<>(latencies));
    }

    private static long pickProduct(int threadIndex, int requestIndex) {
        if (SAME_PRODUCT) {
            return PRODUCT_ID_START;
        }
        int offset = (threadIndex * REQUESTS_PER_THREAD + requestIndex) % PRODUCT_COUNT;
        return PRODUCT_ID_START + offset;
    }

    private static HttpRequest buildRequest(long productId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/order"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"productId\":" + productId + ",\"quantity\":1}"))
                .build();
    }

    private static void report(long costMs, int success, int outOfStock, int failed, List<Long> latencies) {
        Collections.sort(latencies);
        int total = success + outOfStock + failed;

        System.out.println("================ 压测结果 ================");
        System.out.printf("模式      : %s%n", SAME_PRODUCT ? "单商品（热点）" : "多商品（分散）");
        System.out.printf("并发      : %d 线程 × %d 次 = %d 请求%n",
                CONCURRENCY, REQUESTS_PER_THREAD, total);
        System.out.printf("总耗时    : %d ms%n", costMs);
        System.out.printf("QPS       : %.2f%n", total * 1000.0 / Math.max(1, costMs));
        System.out.println("----------------------------------------");
        System.out.printf("成功      : %d%n", success);
        System.out.printf("库存不足  : %d%n", outOfStock);
        System.out.printf("其他失败  : %d%n", failed);
        System.out.println("----------------------------------------");
        System.out.printf("P50       : %d ms%n", percentile(latencies, 0.50));
        System.out.printf("P95       : %d ms%n", percentile(latencies, 0.95));
        System.out.printf("P99       : %d ms%n", percentile(latencies, 0.99));
        System.out.printf("Max       : %d ms%n", latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1));
        System.out.println("========================================");
    }

    private static long percentile(List<Long> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.min(sorted.size() - 1, Math.floor(sorted.size() * q));
        return sorted.get(idx);
    }
}
