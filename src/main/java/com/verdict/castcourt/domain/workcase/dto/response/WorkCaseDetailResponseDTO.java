package com.verdict.castcourt.domain.workcase.dto.response;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;

import java.util.List;

public record WorkCaseDetailResponseDTO(
    int workId,
    String question,
    String suspect,
    String victim,
    String summary,
    String progress,
    List<EvidenceCardDTO> cards,
    List<PenaltyTierDTO> penalties,
    DifficultyResponseDTO difficulty,
    WeightSumResponseDTO weightSum
) {
    public static WorkCaseDetailResponseDTO of(WorkCaseEntity workCase,
                                               EvidenceCardResponseDTO cards,
                                               List<PenaltyTierDTO> penalties) {
        int def = cards.getDefenseTotalWeight();
        int pro = cards.getProsecutionTotalWeight();

        return new WorkCaseDetailResponseDTO(
                workCase.getWorkId(),
                workCase.getQuestion(), workCase.getSuspect(), workCase.getVictim(),
                workCase.getSummary(), workCase.getProgress(),
                cards.getEvidenceCards(),
                penalties,
                DifficultyResponseDTO.of(def, pro),
                new WeightSumResponseDTO(def, pro)
        );
    }
}
