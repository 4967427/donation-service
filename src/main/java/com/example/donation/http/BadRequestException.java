package com.example.donation.http;

/**
 * 表示由客户端输入导致的可预期校验失败。
 *
 * <p>使用专门异常区分“请求错误”和“服务内部错误”，HTTP 入口可分别返回 400 和 500。</p>
 */
final class BadRequestException extends Exception {
    BadRequestException(String message) {
        super(message);
    }
}
