package com.example.donation.service;

import com.example.donation.donation.DonationStore;
import com.example.donation.service.result.DonationResult;

/**
 * 提交捐赠的业务服务。
 *
 * <p>会话校验和数据写入在此处完成，HTTP Handler 不再直接操作仓库。</p>
 */
public final class DonationService {
    /** 患者会话服务，用于将有效会话密钥解析为患者 ID。 */
    private final SessionService sessions;

    /** 捐赠数据存储，负责以线程安全方式追加每一次积分捐赠。 */
    private final DonationStore donations;

    /**
     * 创建捐赠业务服务。
     *
     * @param sessions  患者会话服务
     * @param donations 捐赠数据存储
     */
    public DonationService(SessionService sessions, DonationStore donations) {
        this.sessions = sessions;
        this.donations = donations;
    }

    /** 使用有效患者会话向指定科室追加一次独立捐赠。 */
    public DonationResult donate(int departmentId, String sessionKey, int points) {
        Integer patientId = sessions.resolvePatient(sessionKey);
        if (patientId == null) {
            return DonationResult.INVALID_SESSION;
        }
        donations.add(departmentId, patientId, points);
        return DonationResult.ACCEPTED;
    }
}
