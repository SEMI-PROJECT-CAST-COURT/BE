package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.repository.MyTrialRow;

/* 내 재판 목록의 재판 한 건 */
public record MyTrialDTO(
        int trialId,
        String workTitle,
        String question,        // 쟁점
        Side role,              // 내가 맡은 역할
        int penaltyTier,        // 형벌 단계 1~6
        boolean caseDeleted     // 사건이나 작품이 삭제됨 (is_usage = false)
) {

    public static MyTrialDTO from(MyTrialRow row) {

        boolean caseDeleted = !Boolean.TRUE.equals(row.getCaseUsage())
                || !Boolean.TRUE.equals(row.getWorkUsage());

        return new MyTrialDTO(
                row.getTrialId(),
                row.getCaseSnapshot().workTitle(),
                row.getCaseSnapshot().question(),
                row.getRole(),
                row.getPenaltyTier(),
                caseDeleted
        );
    }
}
