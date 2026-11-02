package com.example.audiobackend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

/**
 * 集成测试的公共设施：读配置、连 JDBC、连 Redis、发 HTTP。
 *
 * <p><b>为什么从 application.yml 读连接信息，而不是在测试里写死：</b>
 * 那个文件里有真实密码，而且已经在 .gitignore 里；写死在测试代码里等于把密码提交进版本库。
 *
 * <p><b>为什么不启动 Spring 上下文：</b>@SpringBootTest 会连带启动 RabbitMQ 监听容器，
 * 于是多了一个消费者和正在跑的应用抢消息，测试结果就不确定了；定时任务也会一起跑。
 * 这里用纯 JDBC + 原生 HttpClient，测的是"用户真正打的那个接口"。
 */
final class TestEnv {

    static final String BASE_URL = "http://localhost:8080";

    private static final PropertySource<?> CFG = loadConfig();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private TestEnv() {
    }

    private static PropertySource<?> loadConfig() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"));
            if (sources.isEmpty()) {
                throw new IllegalStateException("application.yml 是空的");
            }
            return sources.get(0);
        } catch (IOException e) {
            throw new IllegalStateException("读不到 application.yml", e);
        }
    }

    private static String cfg(String key, String fallback) {
        Object value = CFG.getProperty(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(
                cfg("spring.datasource.url", "jdbc:mysql://localhost:3307/audio_mall"),
                cfg("spring.datasource.username", "root"),
                cfg("spring.datasource.password", ""));
    }

    /** 调用方负责在结束时 destroy，否则线程池不释放，测试 JVM 不退出 */
    static StringRedisTemplate redis() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                cfg("spring.data.redis.host", "localhost"),
                Integer.parseInt(cfg("spring.data.redis.port", "6379")));
        String password = cfg("spring.data.redis.password", "");
        if (!password.isEmpty()) {
            // 这个版本的 setPassword 收 String；给空串会被当成"设了个空密码"而连不上
            factory.setPassword(password);
        }
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }

    /** 应用没起来时让测试变成 skipped 而不是 failed —— 这是环境问题，不是代码问题 */
    static boolean appIsUp() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/product/1"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return http(req).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    static HttpResponse<String> http(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static HttpRequest post(String path, String token, String jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b.build();
    }

    static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }
}
