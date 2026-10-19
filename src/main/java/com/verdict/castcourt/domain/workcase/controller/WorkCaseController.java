package com.verdict.castcourt.domain.workcase.controller;

import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseCreateRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseDetailResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseListResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCasePrepareResponseDTO;
import com.verdict.castcourt.domain.workcase.service.WorkCaseService;
import com.verdict.castcourt.global.response.SingleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
            @PathVariable("workId") int workId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCaseList(workId, 1)
        );
    }

    /* 사건 불러오기 */
    @GetMapping("/api/admin/cases/{caseId}")
    public SingleResponse<WorkCaseDetailResponseDTO> getWorkCase(
            @PathVariable("caseId") int caseId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCase(caseId)
        );
    }

    /* 재판 준비 */
    @GetMapping("/api/cases/{caseId}/setup")
    public SingleResponse<WorkCasePrepareResponseDTO> getSetup(
            @PathVariable("caseId") int caseId
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCasePrepare(caseId, 1)
        );
    }

    /* 사건 등록 */
    @PostMapping("/api/admin/cases")
    public SingleResponse<Integer> registerWorkCase(@RequestBody WorkCaseCreateRequestDTO requestDTO) {

        return new SingleResponse<>(
                201,
                workCaseService.saveWorkCase(requestDTO)
        );
    }
}
