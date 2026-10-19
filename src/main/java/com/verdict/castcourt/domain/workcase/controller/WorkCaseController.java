package com.verdict.castcourt.domain.workcase.controller;

import com.verdict.castcourt.domain.workcase.dto.WorkCaseDetailResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.WorkCaseListResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.WorkCasePrepareResponseDTO;
import com.verdict.castcourt.domain.workcase.service.WorkCaseService;
import com.verdict.castcourt.global.response.SingleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사건")
@RestController
public class WorkCaseController {

    private final WorkCaseService workCaseService;

    @Autowired
    public WorkCaseController(WorkCaseService workCaseService) {
        this.workCaseService = workCaseService;
    }

    /* 사건 목록 조회 */
    @GetMapping("/api/works/{workId}/cases")
    public SingleResponse<WorkCaseListResponseDTO> getWorkCases(
            @PathVariable("workId") long workId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCaseList(workId, 1)
        );
    }

    /* 사건 불러오기 */
    @GetMapping("/api/admin/cases/{caseId}")
    public SingleResponse<WorkCaseDetailResponseDTO> getWorkCase(
            @PathVariable("caseId") long caseId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCase(caseId, 1)
        );
    }

    /* 재판 준비 */
    @GetMapping("/api/cases/{caseId}/setup")
    public SingleResponse<WorkCasePrepareResponseDTO> getSetup(
            @PathVariable("caseId") long caseId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCasePrepare((int) caseId, 1)
        );
    }
}
