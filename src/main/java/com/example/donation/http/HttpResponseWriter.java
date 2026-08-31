package com.example.donation.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 响应输出工具。
 *
 * <p>集中设置编码、Content-Type 和响应长度，保证三个接口的输出行为一致。
 * 所有文本都显式使用 UTF-8，避免依赖操作系统默认编码。</p>
 */
final class HttpResponseWriter {
    private HttpResponseWriter() {
    }

    /**
     * 设置所有响应共享的安全与缓存头。
     * 会话密钥和实时榜单不应被代理或浏览器缓存。
     */
    static void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    }

    /**
     * 发送无响应体状态码，例如捐赠成功的 204 或无效会话的 403。
     * {@code -1} 告诉 JDK HttpServer 不创建响应体流。
     */
    static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    /**
     * 发送 UTF-8 文本响应。
     *
     * @param status HTTP 状态码
     * @param body 响应正文，不允许为 {@code null}
     * @param contentType 完整媒体类型，例如 {@code text/csv; charset=utf-8}
     */
    static void sendText(HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
