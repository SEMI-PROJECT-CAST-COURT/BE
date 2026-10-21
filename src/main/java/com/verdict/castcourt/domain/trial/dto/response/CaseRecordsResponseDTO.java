package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;

import java.util.Comparator;
import java.util.List;

/* 사건 기록 요약 응답 (TRL-06) : 이 사건에서 내가 끝낸 역할별 기록
 *  역할마다 기록은 최대 1개(UNIQUE user_id · work_case_id · role)이고, 없으면 null 로 보낸다.
 * */
public record CaseRecordsResponseDTO(
        RoleRecord def,         // 변호인 기록 · 없으면 null
        RoleRecord pro,         // 검사 기록 · 없으면 null
        Side latest             // 나중에 끝낸 쪽(trialId 가 큰 쪽) · 화면이 먼저 보여 줄 탭 · 기록이 없으면 null
) {

    /* 역할 하나의 기록 */
    public record RoleRecord(
            Integer trialId,    // 재판 기록(TRL-05) 조회에 쓰는 id
            TrialMode mode,
            int score,
            int penaltyTier
    ) {

        static RoleRecord from(TrialEntity trial) {
            return new RoleRecord(trial.getId(), trial.getMode(), trial.getScore(), trial.getPenaltyTier());
        }
    }

    /* trials = 이 사용자 · 이 사건의 재판 (0~2개) */
    public static CaseRecordsResponseDTO of(List<TrialEntity> trials) {
        return new CaseRecordsResponseDTO(
                recordOf(trials, Side.DEFENSE),
                recordOf(trials, Side.PROSECUTION),
                trials.stream()
                        .max(Comparator.comparingInt(TrialEntity::getId))
                        .map(TrialEntity::getRole)
                        .orElse(null)
        );
    }

    // 그 역할의 기록 · 없으면 null
    private static RoleRecord recordOf(List<TrialEntity> trials, Side role) {
        return trials.stream()
                .filter(trial -> trial.getRole() == role)
                .findFirst()
                .map(RoleRecord::from)
                .orElse(null);
    }
}
