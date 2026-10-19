package com.verdict.castcourt.domain.workcase.dto;

import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;

/* 사건의 세부 정보가 아닌 사건 카드에 들어가는 내용만 담는 DTO */
public record WorkCaseSummaryResponseDTO(
   long caseId,
   String suspect,
   String victim,
   String question,
   DifficultyResponseDTO difficulty,
   MyVerdictsResponse my
) {
    // 판결 결과
    public record VerdictResponse(int penaltyTier, String sentence) {}
    // 내 판결 결과
    public record MyVerdictsResponse(VerdictResponse def, VerdictResponse pro) {}

    public static WorkCaseSummaryResponseDTO of(WorkCaseEntity workCase,
                                                DifficultyResponseDTO difficulty,
                                                MyVerdictsResponse my) {

        return new WorkCaseSummaryResponseDTO(
                workCase.getId(),
                workCase.getSuspect(),
                workCase.getVictim(),
                workCase.getQuestion(),
                difficulty,
                my
        );
    }
}
