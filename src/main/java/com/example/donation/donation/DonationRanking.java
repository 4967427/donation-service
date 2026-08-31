package com.example.donation.donation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 科室榜单计算器。
 *
 * <p>计算分为三步：</p>
 * <ol>
 *   <li>遍历指定科室的全部独立捐赠记录；</li>
 *   <li>按患者归并，只取其单次捐赠最高值；</li>
 *   <li>按积分降序、患者 ID 升序排序并截取前 20 名。</li>
 * </ol>
 *
 * <p>积分相同时使用患者 ID 作为次级排序键，使相同数据每次查询都得到稳定结果。</p>
 */
final class DonationRanking {
    private static final int MAX_RANKING_SIZE = 20;

    /**
     * 将捐赠记录计算为接口要求的 {@code 患者ID=积分,患者ID=积分} CSV 字符串。
     */
    String toCsv(Iterable<Donation> records) {
        Map<Integer, Integer> highestByPatient = new HashMap<>();
        for (Donation donation : records) {
            // merge 保留当前值和新值中的较大者，而不是把多次积分累加。
            highestByPatient.merge(donation.patientId(), donation.points(), Math::max);
        }
        List<Map.Entry<Integer, Integer>> ranking = new ArrayList<>(highestByPatient.entrySet());
        ranking.sort(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparingInt(Map.Entry::getKey));
        return format(ranking);
    }

    private String format(List<Map.Entry<Integer, Integer>> ranking) {
        StringBuilder csv = new StringBuilder();
        int limit = Math.min(MAX_RANKING_SIZE, ranking.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                csv.append(',');
            }
            Map.Entry<Integer, Integer> entry = ranking.get(i);
            csv.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return csv.toString();
    }
}
