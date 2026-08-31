package com.example.donation.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 请求参数和请求体解析工具。
 *
 * <p>该类集中处理所有输入边界，避免每个接口各自实现一套不一致的校验规则。
 * 请求体最多读取 64 字节，可防止客户端发送超大内容长期占用堆内存。</p>
 */
final class RequestParser {
    private final int maxRequestBodyBytes;

    RequestParser(int maxRequestBodyBytes) {
        if (maxRequestBodyBytes <= 0) {
            throw new IllegalArgumentException("maxRequestBodyBytes must be greater than zero");
        }
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    /**
     * 读取捐赠接口的纯文本请求体并转换为整数。
     *
     * @throws BadRequestException 请求体为空、超过大小上限或不是合法 {@code int} 时抛出
     */
    int readPoints(InputStream input) throws IOException, BadRequestException {
        String value = readSmallBody(input).trim();
        if (value.isEmpty()) {
            throw new BadRequestException("request body must be an integer");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("request body must be an integer");
        }
    }

    /** 拒绝本不允许携带查询参数的接口请求。 */
    void requireNoQuery(URI uri) throws BadRequestException {
        if (uri.getRawQuery() != null) {
            throw new BadRequestException("unexpected query string");
        }
    }

    /**
     * 读取唯一允许的查询参数。
     *
     * <p>例如捐赠接口只接受 {@code sessionkey=xxx}；重复参数、额外参数、空值和
     * 非法 URL 编码都会被视为错误，避免参数歧义。</p>
     */
    String singleQueryParameter(URI uri, String expectedName) throws BadRequestException {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            throw new BadRequestException("missing " + expectedName);
        }
        String[] pairs = rawQuery.split("&", -1);
        if (pairs.length != 1) {
            throw new BadRequestException("only " + expectedName + " is allowed");
        }
        String[] parts = pairs[0].split("=", 2);
        if (parts.length != 2 || !expectedName.equals(decode(parts[0])) || parts[1].isEmpty()) {
            throw new BadRequestException("missing " + expectedName);
        }
        return decode(parts[1]);
    }

    private String readSmallBody(InputStream input) throws IOException, BadRequestException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[32];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            // 先累计再写入，确保超限内容不会进入内存缓冲区。
            if (total > maxRequestBodyBytes) {
                throw new BadRequestException("request body is too large");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String decode(String value) throws BadRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("invalid URL encoding");
        } catch (java.io.UnsupportedEncodingException impossible) {
            // UTF-8 是所有 Java 实现必须支持的字符集，此分支理论上不可达。
            throw new AssertionError(impossible);
        }
    }
}
