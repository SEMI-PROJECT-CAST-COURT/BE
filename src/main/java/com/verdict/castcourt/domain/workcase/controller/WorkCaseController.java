package com.verdict.castcourt.domain.workcase.controller;

import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseUpdateDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseDetailResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseListResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCasePrepareResponseDTO;
import com.verdict.castcourt.domain.workcase.service.WorkCaseService;
import com.verdict.castcourt.global.response.SingleResponse;
import com.verdict.castcourt.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
                    "각 사건의 형벌표(penalties)를 tier 오름차순으로 함께 응답하며, 형벌표가 없는 사건은 빈 배열입니다. " +
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
    @Operation(summary = "사건 등록",
            description = "관리자가 작품에 사건을 등록합니다. 사건 정보와 근거 카드 목록(cards), 형벌표(penalties)를 함께 저장하고, " +
                    "생성된 사건 id를 응답합니다. 하나라도 검증에 실패하면 사건 · 카드 · 형벌표 모두 저장되지 않습니다. " +
                    "question은 1~100자, suspect · victim은 1~50자, summary는 1~1000자, progress는 1~1000자입니다. " +
                    "근거 카드는 side(PROSECUTION / DEFENSE)별로 3장 이상이어야 하며, title은 1~20자, body는 1~70자, weight는 1~20입니다. " +
                    "형벌표는 tier 1~6을 한 번씩 정확히 6개 등록해야 하며, sentence는 1~50자입니다. " +
                    "입력값이 잘못되면 CASE_INPUT_INVALID(400), progress가 1000자를 넘으면 CASE_PROGRESS_TOO_LONG(400), " +
                    "카드가 진영별 3장 미만이면 CASE_CARD_COUNT_INSUFFICIENT(400), 카드 가중치가 범위를 벗어나면 CARD_WEIGHT_INVALID(400), " +
                    "형벌표 단계가 누락 · 중복되거나 범위를 벗어나면 PENALTY_TIER_COUNT_INVALID(400)를 응답합니다.")
    @PostMapping("/api/admin/cases")
    public SingleResponse<Integer> registerWorkCase(@RequestBody WorkCaseRequestDTO requestDTO) {

        return new SingleResponse<>(
                201,
                workCaseService.saveWorkCase(requestDTO)
        );
    }

    /* 사건 수정 */
    @Operation(summary = "사건 수정",
            description = "관리자가 사건 정보와 근거 카드, 형벌표를 수정합니다. 성공 시 본문 없이 200을 응답합니다. " +
                    "하나라도 검증에 실패하면 사건 · 카드 · 형벌표 모두 수정되지 않습니다. " +
                    "사건 정보(question · suspect · victim · summary · progress)는 전체를 교체하며, " +
                    "question은 1~100자, suspect · victim은 1~50자, summary는 1~1000자, progress는 1~1000자입니다. " +
                    "cards는 남길 카드 목록으로 id가 필수이고 1장 이상이어야 하며, title · body · weight 중 보내지 않은(null) 필드는 기존 값을 유지합니다. " +
                    "title은 1~20자, body는 1~70자, weight는 1~20이며, 카드의 side는 변경할 수 없고 새 카드는 추가할 수 없습니다. " +
                    "deletedCardIds는 삭제할 카드 id 목록이며, cards와 겹치는 id가 있으면 안 되고 삭제 후 side별로 3장 이상 남아야 합니다. " +
                    "penalties는 보낸 tier의 sentence만 수정하며(1~50자), 보내지 않은 tier는 기존 값을 유지합니다. " +
                    "사건이 없거나 삭제된 경우 CASE_NOT_FOUND(404), 입력값이 잘못되거나 cards와 deletedCardIds가 겹치면 CASE_INPUT_INVALID(400), " +
                    "progress가 1000자를 넘으면 CASE_PROGRESS_TOO_LONG(400), 카드 가중치가 범위를 벗어나면 CARD_WEIGHT_INVALID(400), " +
                    "사건에 없는 카드 id가 포함되면 EVIDENCE_CARD_NOT_FOUND(404), 삭제 후 카드가 side별 3장 미만이면 CASE_CARD_COUNT_INSUFFICIENT(400), " +
                    "형벌표 단계가 중복되거나 범위를 벗어나면 PENALTY_TIER_COUNT_INVALID(400)를 응답합니다.")
    @PutMapping("/api/admin/cases/{caseId}")
    public void updateWorkCase(
            @PathVariable("caseId") int caseId,
            @RequestBody WorkCaseUpdateDTO requestDTO
    ) {
        workCaseService.updateWorkCase(caseId, requestDTO);
    }

    /* 사건 삭제 */
    @Operation(summary = "사건 삭제",
            description = "관리자가 사건을 삭제합니다. 성공 시 본문 없이 204를 응답합니다. " +
                    "실제 데이터를 지우지 않고 삭제 여부(is_usage)만 변경하는 소프트 삭제이며, " +
                    "삭제된 사건은 목록 · 상세 · 재판 준비 조회에서 제외됩니다. 근거 카드와 형벌표, 재판 기록은 그대로 유지됩니다. " +
                    "사건이 없거나 이미 삭제된 경우 CASE_NOT_FOUND(404)를 응답합니다.")
    @DeleteMapping("/api/admin/cases/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkCase(@PathVariable("caseId") int caseId) {
        workCaseService.deleteWorkCase(caseId);
    }
}
