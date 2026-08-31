package com.example.donation.service;

import com.example.donation.session.SessionManager;

/** 患者会话用例，隔离 HTTP 层与底层会话存储实现。 */
public final class SessionService {
    /** 会话存储与过期校验组件，负责维护密钥和患者之间的对应关系。 */
    private final SessionManager sessions;

    /**
     * 创建患者会话服务。
     *
     * @param sessions 会话存储与校验组件
     */
    public SessionService(SessionManager sessions) {
        this.sessions = sessions;
    }

    /** 创建或获取患者当前有效的会话密钥。 */
    public String getSession(int patientId) {
        return sessions.getOrCreate(patientId);
    }

    /** 根据密钥解析患者；无效或过期时返回 {@code null}。 */
    public Integer resolvePatient(String sessionKey) {
        return sessions.patientFor(sessionKey);
    }
}
