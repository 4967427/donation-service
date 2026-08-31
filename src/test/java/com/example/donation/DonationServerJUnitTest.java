package com.example.donation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 爱心积分服务的标准 JUnit 5 接口测试。
 *
 * <p>测试启动真实的本地 {@link DonationServer}，通过 JDK HttpClient 发出 HTTP 请求，
 * 因而同时覆盖路由、参数解析、业务状态和响应格式，而不是仅测试单个内部方法。</p>
 */
@DisplayName("爱心积分 HTTP 接口")
class DonationServerJUnitTest {
    private static DonationServer server;
    private static HttpClient client;
    private static String baseUrl;

    /** 在全部测试开始前启动一次随机端口服务，避免与开发环境的 8001 端口冲突。 */
    @BeforeAll
    static void startServer() throws Exception {
        server = new DonationServer(0);
        server.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + server.port();
    }

    /** 全部用例结束后释放监听端口和工作线程。 */
    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Test
    @DisplayName("十分钟内重复获取患者会话时返回同一合法密钥")
    void shouldReuseValidPatientSession() throws Exception {
        HttpResponse<String> first = get("/1234/session");
        HttpResponse<String> second = get("/1234/session");

        assertEquals(200, first.statusCode());
        assertTrue(first.body().matches("[a-z0-9]+"));
        assertEquals(first.body(), second.body());
    }

    @Test
    @DisplayName("无效会话提交捐赠时返回403且响应体为空")
    void shouldRejectInvalidSession() throws Exception {
        HttpResponse<String> response = post("/888/donate?sessionkey=notvalid", "50");

        assertEquals(403, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    @DisplayName("同一患者只以单次最高积分进入降序榜单")
    void shouldRankHighestSingleDonation() throws Exception {
        donate(2001, 888, 10);
        donate(2001, 888, 50);
        donate(2001, 888, 30);
        donate(2002, 888, 40);

        assertEquals("2001=50,2002=40", get("/888/topdonors").body());
        assertEquals("", get("/999/topdonors").body());
    }

    @Test
    @DisplayName("科室榜单最多返回20位患者")
    void shouldLimitRankingToTwentyPatients() throws Exception {
        for (int i = 1; i <= 25; i++) {
            donate(3000 + i, 777, i);
        }

        String[] rows = get("/777/topdonors").body().split(",");
        assertEquals(20, rows.length);
        assertEquals("3025=25", rows[0]);
        assertEquals("3006=6", rows[19]);
    }

    @Test
    @DisplayName("100个并发捐赠请求不会丢失最高积分")
    void shouldHandleConcurrentDonationsSafely() throws Exception {
        String key = get("/5000/session").body();
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int points = 1; points <= 100; points++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/555/donate?sessionkey=" + key))
                    .POST(HttpRequest.BodyPublishers.ofString(Integer.toString(points)))
                    .build();
            futures.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            assertEquals(204, future.join().statusCode());
        }
        assertEquals("5000=100", get("/555/topdonors").body());
    }

    @Test
    @DisplayName("非法ID、积分和未知接口返回正确错误状态")
    void shouldRejectMalformedRequests() throws Exception {
        assertEquals(400, get("/abc/session").statusCode());
        String key = get("/6000/session").body();
        assertEquals(400, post("/1/donate?sessionkey=" + key, "oops").statusCode());
        assertEquals(404, get("/6000/unknown").statusCode());
    }

    private static void donate(int patientId, int departmentId, int points) throws Exception {
        String key = get("/" + patientId + "/session").body();
        HttpResponse<String> response = post(
                "/" + departmentId + "/donate?sessionkey=" + key,
                Integer.toString(points));
        assertEquals(204, response.statusCode());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
