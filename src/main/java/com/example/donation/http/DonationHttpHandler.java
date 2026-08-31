package com.example.donation.http;

import com.example.donation.donation.DonationStore;
import com.example.donation.session.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * HTTP 请求的统一入口和用例分发器。
 *
 * <p>本类根据“请求方法 + 路径动作”把请求分发到获取会话、提交捐赠和查询榜单
 * 三个用例。协议层只负责参数校验和状态码输出，业务状态由会话管理器和捐赠仓库维护。</p>
 *
 * <p>每个请求都在 {@code finally} 中关闭 {@link HttpExchange}，防止连接相关资源泄漏。</p>
 */
public final class DonationHttpHandler implements HttpHandler {
    private final SessionManager sessions;
    private final DonationStore donations;

    /**
     * @param sessions 负责创建、校验和清理患者会话
     * @param donations 负责永久保存进程生命周期内的捐赠记录并生成榜单
     */
    public DonationHttpHandler(SessionManager sessions, DonationStore donations) {
        this.sessions = sessions;
        this.donations = donations;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            HttpResponseWriter.addCommonHeaders(exchange);
            dispatch(exchange, Route.parse(exchange.getRequestURI()));
        } catch (BadRequestException e) {
            // 所有可预期的输入格式错误统一映射为 400，并返回简短错误原因。
            HttpResponseWriter.sendText(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (Exception e) {
            // 未预期异常不向客户端暴露堆栈或内部实现，只记录到服务端标准错误流。
            e.printStackTrace(System.err);
            HttpResponseWriter.sendEmpty(exchange, 500);
        } finally {
            exchange.close();
        }
    }

    private void dispatch(HttpExchange exchange, Route route) throws IOException, BadRequestException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method) && "session".equals(route.action())) {
            getSession(exchange, route.id());
        } else if ("POST".equals(method) && "donate".equals(route.action())) {
            donate(exchange, route.id());
        } else if ("GET".equals(method) && "topdonors".equals(route.action())) {
            getTopDonors(exchange, route.id());
        } else {
            // 路径格式正确但方法或动作不存在时返回 404。
            HttpResponseWriter.sendEmpty(exchange, 404);
        }
    }

    private void getSession(HttpExchange exchange, int patientId) throws IOException, BadRequestException {
        RequestParser.requireNoQuery(exchange.getRequestURI());
        HttpResponseWriter.sendText(exchange, 200, sessions.getOrCreate(patientId),
                "text/plain; charset=utf-8");
    }

    private void donate(HttpExchange exchange, int departmentId) throws IOException, BadRequestException {
        String key = RequestParser.singleQueryParameter(exchange.getRequestURI(), "sessionkey");
        Integer patientId = sessions.patientFor(key);
        if (patientId == null) {
            // 非法会话必须在写入任何积分数据之前被拒绝；响应体按需求保持为空。
            RequestParser.drainSmallBody(exchange.getRequestBody());
            HttpResponseWriter.sendEmpty(exchange, 403);
            return;
        }
        donations.add(departmentId, patientId, RequestParser.readPoints(exchange.getRequestBody()));
        HttpResponseWriter.sendEmpty(exchange, 204);
    }

    private void getTopDonors(HttpExchange exchange, int departmentId)
            throws IOException, BadRequestException {
        RequestParser.requireNoQuery(exchange.getRequestURI());
        HttpResponseWriter.sendText(exchange, 200, donations.topDonorsCsv(departmentId),
                "text/csv; charset=utf-8");
    }
}
