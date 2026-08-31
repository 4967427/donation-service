package com.example.donation.service.result;

/** 提交捐赠用例的业务结果，HTTP 层负责将其转换为状态码。 */
public enum DonationResult {
    /** 会话有效，捐赠记录已经成功写入内存。 */
    ACCEPTED,

    /** 会话密钥不存在、已过期，或者不符合有效会话要求。 */
    INVALID_SESSION
}
