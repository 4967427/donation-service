package com.example.donation.session;

/**
 * 不可变的患者会话值对象。
 *
 * <p>对象创建后患者、密钥和过期时刻都不会改变，因此可以安全地在多个请求线程间共享，
 * 不需要额外同步。{@code expiresAt} 使用 {@link java.time.Clock#millis()} 对应的毫秒时间戳。</p>
 */
final class Session {
    private final int patientId;
    private final String key;
    private final long expiresAt;

    Session(int patientId, String key, long expiresAt) {
        this.patientId = patientId;
        this.key = key;
        this.expiresAt = expiresAt;
    }

    /** 返回该会话所属患者的唯一整数 ID。 */
    int patientId() {
        return patientId;
    }

    /** 返回只含小写字母和数字的随机会话密钥。 */
    String key() {
        return key;
    }

    /**
     * 判断指定时刻会话是否仍有效。到达过期时刻本身即视为失效，避免边界多存活 1 毫秒。
     */
    boolean isValidAt(long timestamp) {
        return expiresAt > timestamp;
    }
}
