package com.verdict.castcourt.domain.workcase.controller;

import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseCreateRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseDetailResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseListResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCasePrepareResponseDTO;
import com.verdict.castcourt.domain.workcase.service.WorkCaseService;
import com.verdict.castcourt.global.response.SingleResponse;
import com.verdict.castcourt.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @Operation(summary = "사건 목록 조회",
            description = "작품에 속한 사건 목록을 조회합니다. 삭제된 사건은 제외됩니다. " +
                    "각 사건의 변호인(def)/검사(pro) 난이도는 근거 카드 가중치 합의 차이로 계산하며, " +
                    "내 측 합이 상대보다 6 이상 크면 EASY, 6 이상 작으면 HARD, 그 외에는 NORMAL입니다. " +
                    "progress는 로그인한 사용자의 진행도(done: 진행한 사건 수, total: 전체 사건 수)입니다. " +
                    "my(내 판결)는 재판 기능 구현 전까지 null로 응답합니다.")
    @GetMapping("/api/works/{workId}/cases")
    public SingleResponse<WorkCaseListResponseDTO> getWorkCases(
            @PathVariable("workId") int workId,
            @AuthenticationPrincipal CustomUserDetails user
            ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCaseList(workId, user.getId())
        );
    }

    /* 사건 불러오기 */
    @Operation(summary = "사건 불러오기",
            description = "관리자 사건 수정 화면에서 사용할 사건 상세 정보를 조회합니다. " +
                    "사건 정보와 근거 카드 목록, 형벌표, 역할별 난이도, 역할별 근거 카드 가중치 합(weightSum)을 함께 응답합니다. " +
                    "사건이 없거나 삭제된 경우 CASE_NOT_FOUND(404)를 응답합니다.")
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
    @Operation(summary = "재판 준비",
            description = "재판 시작 전 준비 화면에 필요한 정보를 조회합니다. " +
                    "사건 정보(case)와 역할별 난이도 및 재판 기록 여부(roles), 형벌표(penalties)를 응답합니다. " +
                    "recorded는 사용자가 해당 역할로 재판을 진행한 기록이 있으면 true입니다. " +
                    "사건이 없거나 삭제된 경우 CASE_NOT_FOUND(404)를 응답합니다.")
    @GetMapping("/api/cases/{caseId}/setup")
    public SingleResponse<WorkCasePrepareResponseDTO> getSetup(
            @PathVariable("caseId") int caseId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return new SingleResponse<>(
                200,
                workCaseService.getWorkCasePrepare(caseId, user.getId())
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
