package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.repository.TrialStatRow;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;

import java.util.Arrays;
import java.util.List;

/* 내 통계 응답 (TRL-07) : 판결이 난 내 재판 전체의 역할별 승패 · 형벌 단계 분포 */
public record MyStatsResponseDTO(
        int total,                  // 전체 판 수
        ByRole byRole,
        Distribution distribution
) {

    // 형벌 단계 수 (1~6)
    private static final int TIER_COUNT = 6;

    /* 역할별 승패 */
    public record ByRole(
            RoleStats def,
            RoleStats pro
    ) {
    }

    /* 역할 하나의 판 수 · 승 · 패 (승패 기준은 ScoreRule.isWin) */
    public record RoleStats(
            int total,
            int win,
            int lose
    ) {

        // tierCounts[0] = 1단계 판 수 … tierCounts[5] = 6단계 판 수
        static RoleStats of(Side role, int[] tierCounts) {
            int win = 0;
            int lose = 0;
            for (int tier = 1; tier <= TIER_COUNT; tier++) {
                int count = tierCounts[tier - 1];
                if (ScoreRule.isWin(role, tier)) {
                    win += count;
                } else {
                    lose += count;
                }
            }
            return new RoleStats(win + lose, win, lose);
        }
    }

    /* 형벌 단계 1~6 별 판 수 (배열 0번 = 1단계) */
    public record Distribution(
            List<Integer> def,
            List<Integer> pro
    ) {
    }

    /* rows = (역할, 형벌 단계)별 판 수 · 판이 없는 칸은 줄이 없어서 0 으로 채운다 */
    public static MyStatsResponseDTO from(List<TrialStatRow> rows) {

        int[] defCounts = new int[TIER_COUNT];
        int[] proCounts = new int[TIER_COUNT];
        for (TrialStatRow row : rows) {
            int[] counts = row.getRole() == Side.DEFENSE ? defCounts : proCounts;
            counts[row.getPenaltyTier() - 1] += row.getTrialCount().intValue();
        }

        RoleStats def = RoleStats.of(Side.DEFENSE, defCounts);
        RoleStats pro = RoleStats.of(Side.PROSECUTION, proCounts);

        return new MyStatsResponseDTO(
                def.total() + pro.total(),
                new ByRole(def, pro),
                new Distribution(toList(defCounts), toList(proCounts))
        );
    }

    private static List<Integer> toList(int[] counts) {
        return Arrays.stream(counts).boxed().toList();
    }
}
