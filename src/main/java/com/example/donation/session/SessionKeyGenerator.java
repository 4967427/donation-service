package com.example.donation.session;

import java.security.SecureRandom;

/**
 * 会话密钥生成器。
 *
 * <p>使用 {@link SecureRandom} 而非普通伪随机数，降低密钥被猜中的概率。字符集严格限制为
 * 26 个小写字母和 10 个数字，满足接口格式要求；24 位长度提供足够大的密钥空间。</p>
 */
final class SessionKeyGenerator {
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int KEY_LENGTH = 24;
    private final SecureRandom random = new SecureRandom();

    /** 生成一个新的 24 位随机密钥。 */
    String nextKey() {
        char[] value = new char[KEY_LENGTH];
        for (int i = 0; i < value.length; i++) {
            value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
