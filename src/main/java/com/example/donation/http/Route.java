package com.example.donation.http;

import java.net.URI;

/**
 * 解析后的 {@code /{id}/{action}} 路径值对象。
 *
 * <p>{@code id} 在不同接口中分别代表患者 ID 或科室 ID；{@code action} 表示
 * {@code session}、{@code donate} 或 {@code topdonors}。这里只校验通用路径结构，
 * 具体动作是否合法由 {@link DonationHttpHandler} 判断。</p>
 */
final class Route {
    private final int id;
    private final String action;

    private Route(int id, String action) {
        this.id = id;
        this.action = action;
    }

    static Route parse(URI uri) throws BadRequestException {
        // 使用 -1 保留末尾空段，使 /123/ 不能被误判为合法路径。
        String[] parts = uri.getPath().split("/", -1);
        if (parts.length != 3 || !parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new BadRequestException("path must be /{id}/{action}");
        }
        try {
            int id = Integer.parseInt(parts[1]);
            // 需求中的 ID 为整数标识；负数没有业务含义，因此在协议入口拒绝。
            if (id < 0) {
                throw new NumberFormatException();
            }
            return new Route(id, parts[2]);
        } catch (NumberFormatException e) {
            throw new BadRequestException("id must be a non-negative integer");
        }
    }

    int id() {
        return id;
    }

    String action() {
        return action;
    }
}
