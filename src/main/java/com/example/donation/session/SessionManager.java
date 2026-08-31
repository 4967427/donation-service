package com.example.donation.session;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程安全的会话管理器，负责创建、复用、校验、过期和清理患者会话。
 *
 * <p>内部维护两个索引：</p>
 * <ul>
 *   <li>{@code byPatient}：保证同一患者在十分钟有效期内始终获得同一密钥；</li>
 *   <li>{@code byKey}：让捐赠接口可以根据密钥快速反查患者。</li>
 * </ul>
 *
 * <p>两个索引均使用 {@link ConcurrentHashMap}。患者索引上的 {@code compute} 对单个患者
 * 原子执行，因此同一患者即使并发请求会话，也只会留下一个有效会话。</p>
 */
public final class SessionManager {
    private final Clock clock;
    private final long ttlMillis;
    private final SessionKeyGenerator keyGenerator = new SessionKeyGenerator();
    private final ConcurrentHashMap<Integer, Session> byPatient = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Session> byKey = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    /**
     * @param clock 时间源；生产环境使用系统时钟，测试可传入可控时钟
     * @param ttl 会话从创建时刻开始计算的固定有效期
     */
    public SessionManager(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttlMillis = ttl.toMillis();
    }

    /**
     * 获取患者现有有效密钥，或在不存在/已过期时创建新密钥。
     *
     * @param patientId 患者唯一 ID
     * @return 当前有效的会话密钥
     */
    public String getOrCreate(int patientId) {
        long now = clock.millis();
        Session current = byPatient.compute(patientId, (id, existing) -> {
            if (existing != null && existing.isValidAt(now)) {
                // 有效期不因重复获取而续期，始终从首次创建时刻计算十分钟。
                return existing;
            }
            if (existing != null) {
                byKey.remove(existing.key(), existing);
            }
            Session created;
            do {
                created = new Session(id, keyGenerator.nextKey(), now + ttlMillis);
                // 极低概率出现随机密钥碰撞时重新生成，绝不覆盖其他患者会话。
            } while (byKey.putIfAbsent(created.key(), created) != null);
            return created;
        });
        cleanOccasionally(now);
        return current.key();
    }

    /**
     * 根据会话密钥查找患者。
     *
     * @return 有效会话对应的患者 ID；密钥不存在或已经过期时返回 {@code null}
     */
    public Integer patientFor(String key) {
        long now = clock.millis();
        Session session = byKey.get(key);
        if (session == null || !session.isValidAt(now)) {
            removeExpired(key, session);
            cleanOccasionally(now);
            return null;
        }
        cleanOccasionally(now);
        return session.patientId();
    }

    private void removeExpired(String key, Session session) {
        if (session != null) {
            byKey.remove(key, session);
            byPatient.remove(session.patientId(), session);
        }
    }

    private void cleanOccasionally(long now) {
        // 每次请求都全表扫描成本过高；每 1024 次操作清理一次，兼顾内存回收和吞吐量。
        if ((operations.incrementAndGet() & 1023L) != 0) {
            return;
        }
        for (Map.Entry<String, Session> entry : byKey.entrySet()) {
            Session session = entry.getValue();
            // remove(key, value) 是条件删除，若并发线程已更新映射，不会误删新值。
            if (!session.isValidAt(now) && byKey.remove(entry.getKey(), session)) {
                byPatient.remove(session.patientId(), session);
            }
        }
    }
}
