package com.verdict.castcourt.domain.workcase.dto.request;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;

import java.util.List;

public record WorkCaseUpdateDTO (
        String question,
        String suspect,
        String victim,
        String summary,
        String progress,
        List<EvidenceCardDTO> cards,
        List<Integer> deletedCardIds,
        List<PenaltyTierDTO> penalties
) {}
