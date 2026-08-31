package com.example.donation.http;

import com.example.donation.service.result.DonationResult;
import com.example.donation.service.DonationService;
import com.example.donation.service.RankingService;
import com.example.donation.service.SessionService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * HTTP 请求的统一入口和用例分发器。
 *
 * <p>本类根据“请求方法 + 路径动作”把请求分发到获取会话、提交捐赠和查询榜单
 * 三个用例。协议层只负责参数校验和状态码输出，业务编排全部交给 service 层。</p>
 *
 * <p>每个请求都在 {@code finally} 中关闭 {@link HttpExchange}，防止连接相关资源泄漏。</p>
 */
public final class DonationHttpHandler implements HttpHandler {
    private final SessionService sessions;
    private final DonationService donations;
    private final RankingService rankings;
    private final RequestParser requestParser;

    /**
     * @param sessions 获取患者会话的业务用例
     * @param donations 提交捐赠的业务用例
     * @param rankings 查询贡献榜的业务用例
     * @param maxRequestBodyBytes HTTP 请求体大小上限
     */
    public DonationHttpHandler(
            SessionService sessions,
            DonationService donations,
            RankingService rankings,
            int maxRequestBodyBytes) {
        this.sessions = sessions;
        this.donations = donations;
        this.rankings = rankings;
        this.requestParser = new RequestParser(maxRequestBodyBytes);
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
        requestParser.requireNoQuery(exchange.getRequestURI());
        HttpResponseWriter.sendText(exchange, 200, sessions.getSession(patientId),
                "text/plain; charset=utf-8");
    }

    private void donate(HttpExchange exchange, int departmentId) throws IOException, BadRequestException {
        String key = requestParser.singleQueryParameter(exchange.getRequestURI(), "sessionkey");
        int points = requestParser.readPoints(exchange.getRequestBody());
        DonationResult result = donations.donate(departmentId, key, points);
        if (result == DonationResult.INVALID_SESSION) {
            // 非法会话必须在写入任何积分数据之前被拒绝；响应体按需求保持为空。
            HttpResponseWriter.sendEmpty(exchange, 403);
            return;
        }
        HttpResponseWriter.sendEmpty(exchange, 204);
    }

    private void getTopDonors(HttpExchange exchange, int departmentId)
            throws IOException, BadRequestException {
        requestParser.requireNoQuery(exchange.getRequestURI());
        HttpResponseWriter.sendText(exchange, 200, rankings.topDonorsCsv(departmentId),
                "text/csv; charset=utf-8");
    }
}
