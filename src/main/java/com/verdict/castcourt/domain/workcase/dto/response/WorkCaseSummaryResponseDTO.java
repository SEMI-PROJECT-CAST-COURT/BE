package com.verdict.castcourt.domain.workcase.dto.response;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;

import java.util.List;

/* 사건의 세부 정보가 아닌 사건 카드에 들어가는 내용만 담는 DTO */
public record WorkCaseSummaryResponseDTO(
   long caseId,
   String suspect,
   String victim,
   String question,
   DifficultyResponseDTO difficulty,
   List<PenaltyTierDTO> penalties,
   MyVerdictsResponse my
) {
    // 판결 결과
    public record VerdictResponse(int penaltyTier, String sentence) {}
    // 내 판결 결과
    public record MyVerdictsResponse(VerdictResponse def, VerdictResponse pro) {}

    public static WorkCaseSummaryResponseDTO of(WorkCaseEntity workCase,
                                                DifficultyResponseDTO difficulty,
                                                List<PenaltyTierDTO> penalties,
                                                MyVerdictsResponse my) {

        return new WorkCaseSummaryResponseDTO(
                workCase.getId(),
                workCase.getSuspect(),
                workCase.getVictim(),
                workCase.getQuestion(),
                difficulty,
                penalties,
                my
        );
    }
}
