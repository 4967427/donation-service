package com.example.donation.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务运行参数的不可变配置对象。
 *
 * <p>所有容量、超时和榜单参数都集中在这里，业务类不再保存散落的默认常量。
 * 可通过 {@link Builder} 在代码中配置，也可通过 {@link #fromArgs(String[])} 解析启动参数。</p>
 */
public final class ServerConfig {
    /** HTTP 服务默认监听端口。 */
    private static final int DEFAULT_PORT = 8001;

    /** 患者会话默认有效期。 */
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(10);

    /** 线程池任务队列默认可容纳的等待任务数。 */
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    /** 操作系统等待接受连接队列的默认长度。 */
    private static final int DEFAULT_SOCKET_BACKLOG = 128;

    /** HTTP 请求体默认允许读取的最大字节数。 */
    private static final int DEFAULT_MAX_REQUEST_BODY_BYTES = 64;

    /** 单个科室贡献榜默认最多返回的患者数量。 */
    private static final int DEFAULT_RANKING_SIZE = 20;

    /** HTTP 服务监听端口；允许为 0，此时由操作系统分配可用端口。 */
    private final int port;

    /** 患者会话有效期，从会话创建时刻开始计算。 */
    private final Duration sessionTtl;

    /** 处理 HTTP 请求的固定工作线程数量。 */
    private final int workerThreads;

    /** 工作线程全部繁忙时，等待执行的请求任务上限。 */
    private final int queueCapacity;

    /** 服务端套接字尚未接受的连接排队上限。 */
    private final int socketBacklog;

    /** 单次捐赠请求体允许读取的最大字节数，用于限制内存占用。 */
    private final int maxRequestBodyBytes;

    /** 每个科室贡献榜最多返回的患者数量。 */
    private final int rankingSize;

    private ServerConfig(Builder builder) {
        this.port = requireRange("port", builder.port, 0, 65535);
        this.sessionTtl = requirePositive("sessionTtl", builder.sessionTtl);
        this.workerThreads = requirePositive("workerThreads", builder.workerThreads);
        this.queueCapacity = requirePositive("queueCapacity", builder.queueCapacity);
        this.socketBacklog = requirePositive("socketBacklog", builder.socketBacklog);
        this.maxRequestBodyBytes = requirePositive("maxRequestBodyBytes", builder.maxRequestBodyBytes);
        this.rankingSize = requirePositive("rankingSize", builder.rankingSize);
    }

    /** 返回包含全部默认值的新配置。 */
    public static ServerConfig defaults() {
        return builder().build();
    }

    /** 返回可按需覆盖字段的配置构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 解析命令行参数。支持原有的单个端口参数，也支持以下命名参数：
     *
     * <pre>
     * --port=8001
     * --session-ttl-seconds=600
     * --worker-threads=16
     * --queue-capacity=1024
     * --socket-backlog=128
     * --max-request-body-bytes=64
     * --ranking-size=20
     * </pre>
     */
    public static ServerConfig fromArgs(String[] args) {
        if (args.length == 1 && !args[0].startsWith("--")) {
            return builder().port(parseInt("port", args[0])).build();
        }

        Map<String, String> values = parseNamedArguments(args);
        Builder builder = builder();
        applyInt(values, "port", builder::port);
        if (values.containsKey("session-ttl-seconds")) {
            builder.sessionTtl(Duration.ofSeconds(
                    parseLong("session-ttl-seconds", values.remove("session-ttl-seconds"))));
        }
        applyInt(values, "worker-threads", builder::workerThreads);
        applyInt(values, "queue-capacity", builder::queueCapacity);
        applyInt(values, "socket-backlog", builder::socketBacklog);
        applyInt(values, "max-request-body-bytes", builder::maxRequestBodyBytes);
        applyInt(values, "ranking-size", builder::rankingSize);
        if (!values.isEmpty()) {
            throw new IllegalArgumentException("unknown argument: --" + values.keySet().iterator().next());
        }
        return builder.build();
    }

    public int port() {
        return port;
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    public int workerThreads() {
        return workerThreads;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int socketBacklog() {
        return socketBacklog;
    }

    public int maxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public int rankingSize() {
        return rankingSize;
    }

    private static int defaultWorkerThreads() {
        return Math.max(4, Math.min(32, Runtime.getRuntime().availableProcessors() * 2));
    }

    private static Map<String, String> parseNamedArguments(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (String argument : args) {
            if (!argument.startsWith("--") || !argument.contains("=")) {
                throw new IllegalArgumentException("argument must use --name=value: " + argument);
            }
            String[] parts = argument.substring(2).split("=", 2);
            if (parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new IllegalArgumentException("argument must use --name=value: " + argument);
            }
            if (result.putIfAbsent(parts[0], parts[1]) != null) {
                throw new IllegalArgumentException("duplicate argument: --" + parts[0]);
            }
        }
        return result;
    }

    private static void applyInt(Map<String, String> values, String name, IntConsumer consumer) {
        String value = values.remove(name);
        if (value != null) {
            consumer.accept(parseInt(name, value));
        }
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private static long parseLong(String name, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static int requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static Duration requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(int value);
    }

    /** 用于创建并验证不可变配置的构建器。 */
    public static final class Builder {
        /** 待构建配置的 HTTP 监听端口。 */
        private int port = DEFAULT_PORT;

        /** 待构建配置的患者会话有效期。 */
        private Duration sessionTtl = DEFAULT_SESSION_TTL;

        /** 待构建配置的 HTTP 工作线程数量。 */
        private int workerThreads = defaultWorkerThreads();

        /** 待构建配置的任务等待队列容量。 */
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

        /** 待构建配置的服务端套接字连接队列长度。 */
        private int socketBacklog = DEFAULT_SOCKET_BACKLOG;

        /** 待构建配置的请求体最大字节数。 */
        private int maxRequestBodyBytes = DEFAULT_MAX_REQUEST_BODY_BYTES;

        /** 待构建配置的单科室榜单最大返回数量。 */
        private int rankingSize = DEFAULT_RANKING_SIZE;

        private Builder() {
        }

        public Builder port(int value) {
            this.port = value;
            return this;
        }

        public Builder sessionTtl(Duration value) {
            this.sessionTtl = value;
            return this;
        }

        public Builder workerThreads(int value) {
            this.workerThreads = value;
            return this;
        }

        public Builder queueCapacity(int value) {
            this.queueCapacity = value;
            return this;
        }

        public Builder socketBacklog(int value) {
            this.socketBacklog = value;
            return this;
        }

        public Builder maxRequestBodyBytes(int value) {
            this.maxRequestBodyBytes = value;
            return this;
        }

        public Builder rankingSize(int value) {
            this.rankingSize = value;
            return this;
        }

        public ServerConfig build() {
            return new ServerConfig(this);
        }
    }
}
