package com.verdict.castcourt.domain.workcase.dto.request;

public record CaseDraftRequestDTO(
    String question,
    String suspect,
    String victim,
    String summary,
    String progress,
    String feedback
) {}
