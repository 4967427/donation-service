package com.example.donation.donation;

/**
 * 一次独立捐赠的不可变记录。
 *
 * <p>即使同一患者向同一科室多次捐赠，每次请求也会创建一个新对象并永久保存在
 * 当前服务进程的内存中。榜单仅在查询阶段选择其中的单次最高积分，不会覆盖原记录。</p>
 */
final class Donation {
    private final int patientId;
    private final int points;

    Donation(int patientId, int points) {
        this.patientId = patientId;
        this.points = points;
    }

    /** 返回发起本次捐赠的患者 ID。 */
    int patientId() {
        return patientId;
    }

    /** 返回本次请求提交的积分数值。 */
    int points() {
        return points;
    }
}
