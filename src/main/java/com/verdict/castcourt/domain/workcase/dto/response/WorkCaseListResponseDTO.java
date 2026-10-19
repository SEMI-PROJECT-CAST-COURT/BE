package com.verdict.castcourt.domain.workcase.dto.response;

import java.util.List;

/* 사건 목록을 담는 DTO */
public record WorkCaseListResponseDTO(
        ProgressResponse progress,
        List<WorkCaseSummaryResponseDTO> cases
) {
    // 사건 진행도
    public record ProgressResponse(int done, int total) {}
}
