package com.verdict.castcourt.domain.trial.controller;

import com.verdict.castcourt.domain.trial.dto.MyTrialListDTO;
import com.verdict.castcourt.domain.trial.service.TrialService;
import com.verdict.castcourt.global.response.SingleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrialController {

    private final TrialService trialService;

    @Autowired
    public TrialController(TrialService trialService) {
        this.trialService = trialService;
    }

    // TODO(연결): 로그인이 붙으면 X-User-Id 헤더 대신 로그인 사용자 id 사용
    @Operation(summary = "내 재판 목록",
            description = "판결이 난 내 재판을 최근 순(trialId 내림차순)으로 조회합니다. " +
                    "다음 페이지는 응답의 nextCursor 를 cursor 로 넘기고, 마지막 페이지면 nextCursor 가 null 입니다.")
    @GetMapping("/api/users/me/trials")
    public ResponseEntity<SingleResponse<MyTrialListDTO>> getMyTrials(
            @RequestHeader(name = "X-User-Id", defaultValue = "4") int userId,
            @Parameter(description = "역할 PROSECUTION / DEFENSE") @RequestParam(required = false) String role,
            @Parameter(description = "승패 WIN / LOSE") @RequestParam(required = false) String outcome,
            @Parameter(description = "작품 id") @RequestParam(required = false) Integer workId,
            @Parameter(description = "앞 페이지 응답의 nextCursor") @RequestParam(required = false) String cursor
    ) {
        MyTrialListDTO response = trialService.getMyTrials(userId, role, outcome, workId, cursor);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), response));
    }
}
