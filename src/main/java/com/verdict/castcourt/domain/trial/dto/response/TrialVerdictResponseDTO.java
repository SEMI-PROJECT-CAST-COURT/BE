package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;

/* 판결 받기 응답 · 판결 뒤라 모드와 상관없이 게이지까지 모두 공개한다
 *  결과 이름(완승~완패)은 보내지 않는다. 화면이 역할 + penaltyTier 로 정한다. (명세 v6)
 * */
public record TrialVerdictResponseDTO(
        Integer trialId,            // DB 에 저장된 재판 id (재판 기록 · 내 재판 목록에서 쓰는 id)
        int score,                  // 최종 게이지 0~100
        int penaltyTier,            // 형벌 단계 1~6
        String title,               // 단계 이름 "무죄 1단계" ~ "유죄 3단계" (용어 정리집 형벌 단계 표)
        String sentence,            // 처분 문장 (사건 형벌표의 그 단계)
        String judgeSummary,        // 판사 총평
        boolean oppositeAvailable   // 반대 역할 기록이 아직 없으면 true → "(반대 역할)로 다시하기" 버튼
) {

    public static TrialVerdictResponseDTO of(TrialEntity trial, boolean oppositeAvailable) {
        return new TrialVerdictResponseDTO(
                trial.getId(),
                trial.getScore(),
                trial.getPenaltyTier(),
                ScoreRule.tierTitle(trial.getPenaltyTier()),
                sentenceOf(trial.getCaseSnapshot(), trial.getPenaltyTier()),
                trial.getJudgeSummary(),
                oppositeAvailable
        );
    }

    // 형벌표에서 그 단계의 처분 문장 · 형벌표에 그 단계가 없으면 null
    private static String sentenceOf(CaseSnapshot snapshot, int tier) {
        return snapshot.penalties().stream()
                .filter(penalty -> penalty.tier() == tier)
                .map(CaseSnapshot.Penalty::sentence)
                .findFirst()
                .orElse(null);
    }
}
