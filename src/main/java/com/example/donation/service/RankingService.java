package com.example.donation.service;

import com.example.donation.donation.DonationStore;

/** 查询科室贡献榜的业务服务。 */
public final class RankingService {
    /** 捐赠数据存储，为排行榜查询提供指定科室的积分记录。 */
    private final DonationStore donations;

    /**
     * 创建排行榜查询服务。
     *
     * @param donations 捐赠数据存储
     */
    public RankingService(DonationStore donations) {
        this.donations = donations;
    }

    /** 返回指定科室配置数量以内的贡献榜 CSV。 */
    public String topDonorsCsv(int departmentId) {
        return donations.topDonorsCsv(departmentId);
    }
}
