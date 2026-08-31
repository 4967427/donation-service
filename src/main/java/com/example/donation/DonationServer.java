package com.example.donation;

import com.example.donation.donation.DonationStore;
import com.example.donation.http.DonationHttpHandler;
import com.example.donation.session.SessionManager;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 爱心积分 HTTP 服务的应用入口。
 *
 * <p>本类只负责组装依赖、创建 JDK 原生 {@link HttpServer}、管理工作线程池，
 * 以及启动和停止服务。具体的 HTTP 协议处理、会话管理和捐赠数据处理分别交给
 * {@link DonationHttpHandler}、{@link SessionManager} 和 {@link DonationStore}，
 * 避免入口类承担业务逻辑。</p>
 *
 * <p>服务实现了 {@link AutoCloseable}，因此测试和嵌入式调用可以使用
 * try-with-resources 或显式调用 {@link #close()} 可靠释放监听端口和工作线程。</p>
 */
public final class DonationServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 8001;
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private final HttpServer server;
    private final ThreadPoolExecutor executor;

    public DonationServer(int port) throws IOException {
        this(port, Clock.systemUTC());
    }

    /**
     * 供测试使用的构造方法。注入 {@link Clock} 后，可以在不真实等待十分钟的情况下
     * 测试会话过期行为。
     */
    DonationServer(int port, Clock clock) throws IOException {
        this.executor = createExecutor();
        this.server = HttpServer.create(new InetSocketAddress(port), 128);
        this.server.setExecutor(executor);
        this.server.createContext("/", new DonationHttpHandler(
                new SessionManager(clock, SESSION_TTL), new DonationStore()));
    }

    private static ThreadPoolExecutor createExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(4, Math.min(32, processors * 2));
        // 固定线程数和有界队列共同限制资源占用；队列满时由提交请求的线程执行任务，
        // 形成自然背压，而不是继续无限创建线程或无限堆积请求。
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 开始监听构造时指定的端口。 */
    public void start() {
        server.start();
    }

    /**
     * 返回实际监听端口。传入端口 0 时操作系统会自动分配端口，测试通过此方法取得该端口。
     */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        // 先停止接受新请求，再中断尚未结束的工作线程。
        server.stop(0);
        executor.shutdownNow();
    }

    /**
     * 命令行入口。第一个参数可指定端口，未指定时使用 8001。
     */
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        DonationServer application = new DonationServer(port);
        // JVM 正常退出或收到 Ctrl+C 时释放监听端口与线程池。
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "shutdown-hook"));
        application.start();
        System.out.println("Donation service listening on http://localhost:" + application.port());
    }
}
