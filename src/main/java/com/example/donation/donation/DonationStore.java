package com.example.donation.donation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 线程安全的内存捐赠仓库。
 *
 */
public final class DonationStore {
    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Donation>> byDepartment =
            new ConcurrentHashMap<>();
    private final DonationRanking ranking = new DonationRanking();

    /**
     * 追加一次独立捐赠。该操作不会覆盖或合并患者以前的记录。
     */
    public void add(int departmentId, int patientId, int points) {
        byDepartment.computeIfAbsent(departmentId, ignored -> new ConcurrentLinkedQueue<>())
                .add(new Donation(patientId, points));
    }

    /**
     * 查询指定科室的 TOP20 榜单。
     */
    public String topDonorsCsv(int departmentId) {
        ConcurrentLinkedQueue<Donation> records = byDepartment.get(departmentId);
        if (records == null || records.isEmpty()) {
            return "";
        }
        return ranking.toCsv(records);
    }
}
