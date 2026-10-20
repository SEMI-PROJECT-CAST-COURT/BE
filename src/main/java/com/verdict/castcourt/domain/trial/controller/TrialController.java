package com.verdict.castcourt.domain.trial.controller;

import org.springframework.web.bind.annotation.*;
import com.verdict.castcourt.domain.trial.dto.response.CaseRecordsResponseDTO;
import com.verdict.castcourt.domain.trial.dto.response.MyTrialListDTO;
import com.verdict.castcourt.domain.trial.dto.request.TrialStartRequestDTO;
import com.verdict.castcourt.domain.trial.dto.response.TrialRecordResponseDTO;
import com.verdict.castcourt.domain.trial.dto.response.TrialStartResponseDTO;
import com.verdict.castcourt.domain.trial.dto.response.TrialStatusResponseDTO;
import com.verdict.castcourt.domain.trial.dto.response.TrialVerdictResponseDTO;
import com.verdict.castcourt.domain.trial.service.TrialService;
import com.verdict.castcourt.global.response.SingleResponse;
import com.verdict.castcourt.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
public class TrialController {

    private final TrialService trialService;

    @Autowired
    public TrialController(TrialService trialService) {
        this.trialService = trialService;
    }

    @Operation(summary = "내 재판 목록",
            description = "판결이 난 내 재판을 최근 순(trialId 내림차순)으로 조회합니다. " +
                    "다음 페이지는 응답의 nextCursor 를 cursor 로 넘기고, 마지막 페이지면 nextCursor 가 null 입니다.")
    @GetMapping("/api/users/me/trials")
    public ResponseEntity<SingleResponse<MyTrialListDTO>> getMyTrials(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "역할 PROSECUTION / DEFENSE") @RequestParam(required = false) String role,
            @Parameter(description = "승패 WIN / LOSE") @RequestParam(required = false) String outcome,
            @Parameter(description = "작품 id") @RequestParam(required = false) Integer workId,
            @Parameter(description = "앞 페이지 응답의 nextCursor") @RequestParam(required = false) String cursor
    ) {
        MyTrialListDTO response = trialService.getMyTrials(user.getId(), role, outcome, workId, cursor);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }

    @Operation(summary = "재판 시작",
            description = "진행 중 재판을 서버 메모리에 만들고 trialId 를 돌려줍니다. 같은 사용자가 다시 시작하면 이전 판은 사라집니다. " +
                    "cards 는 CHICKEN, progress 는 CHALLENGER 에서만 옵니다.")
    @PostMapping("/api/trials")
    public ResponseEntity<SingleResponse<TrialStartResponseDTO>> startTrial(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody TrialStartRequestDTO request
    ) {
        TrialStartResponseDTO response = trialService.startTrial(user.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SingleResponse<>(HttpStatus.CREATED.value(), response));
    }

    @Operation(summary = "재판 상태",
            description = "새로고침 때 진행 중 재판 화면을 복원합니다. 모드에 따라 게이지 · 판정 값이 빠집니다.")
    @GetMapping("/api/trials/{trialId}")
    public ResponseEntity<SingleResponse<TrialStatusResponseDTO>> getTrialStatus(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String trialId
    ) {
        TrialStatusResponseDTO response = trialService.getTrialStatus(user.getId(), trialId);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }

    @Operation(summary = "판결 받기",
            description = "최후 변론까지 끝난 재판을 판결하고 DB 에 저장합니다. 저장된 판은 메모리에서 지워져 재판 상태 조회가 404 가 됩니다. " +
                    "AI 판사 총평 때문에 몇 초 걸릴 수 있습니다.")
    @PostMapping("/api/trials/{trialId}/verdict")
    public ResponseEntity<SingleResponse<TrialVerdictResponseDTO>> receiveVerdict(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String trialId
    ) {
        TrialVerdictResponseDTO response = trialService.receiveVerdict(user.getId(), trialId);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }

    @Operation(summary = "나가기",
            description = "진행 중 재판을 메모리에서 지웁니다(DB 저장 없음). 이미 없으면 그대로 204 입니다. " +
                    "나가기 버튼 · 뒤로 가기 · 새로고침 · 탭 닫기 등 화면을 떠날 때 부릅니다.")
    @DeleteMapping("/api/trials/{trialId}")
    public ResponseEntity<Void> exitTrial(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String trialId
    ) {
        trialService.exitTrial(user.getId(), trialId);

        // 204 No Content : 성공했고 돌려줄 본문이 없다 (SingleResponse 로 감싸지 않음)
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "재판 기록",
            description = "판결이 난 내 재판의 발언 전체와 결정적 순간(keyMoments = 게이지를 가장 크게 움직인 발언 seq 3개)을 조회합니다.")
    @GetMapping("/api/trials/{trialId}/record")
    public ResponseEntity<SingleResponse<TrialRecordResponseDTO>> getTrialRecord(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "판결 받기 응답의 trialId (DB id)") @PathVariable Integer trialId
    ) {
        TrialRecordResponseDTO response = trialService.getTrialRecord(user.getId(), trialId);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }

    @Operation(summary = "사건 기록 요약",
            description = "이 사건에서 내가 끝낸 역할별 기록입니다. 기록이 없는 역할은 null, latest 는 나중에 끝낸 역할입니다.")
    @GetMapping("/api/cases/{caseId}/records")
    public ResponseEntity<SingleResponse<CaseRecordsResponseDTO>> getCaseRecords(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Integer caseId
    ) {
        CaseRecordsResponseDTO response = trialService.getCaseRecords(user.getId(), caseId);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }
}
