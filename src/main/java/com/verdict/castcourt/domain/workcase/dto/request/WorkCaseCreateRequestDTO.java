package com.verdict.castcourt.domain.workcase.dto.request;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;

import java.util.List;

public record WorkCaseCreateRequestDTO(
        int workId,
        String question,
        String suspect,
        String victim,
        String summary,
        String progress,
        List<EvidenceCardDTO> cards,
        List<WorkCasePenaltyRequestDTO> penalties
) {}
