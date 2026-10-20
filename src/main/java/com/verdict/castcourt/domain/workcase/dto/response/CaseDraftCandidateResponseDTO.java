package com.verdict.castcourt.domain.workcase.dto.response;

public record CaseDraftCandidateResponseDTO(
        String question,
        String suspect,
        String victim,
        String summary,
        String progress
) {}
