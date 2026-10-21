package com.verdict.castcourt.domain.speaking.controller;

import com.verdict.castcourt.domain.speaking.dto.request.SpeakingRequestDTO;
import com.verdict.castcourt.domain.speaking.service.SpeakingService;
import com.verdict.castcourt.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SpeakingController {

    private final SpeakingService speakingService;

    @Autowired
    public SpeakingController(SpeakingService speakingService) {
        this.speakingService = speakingService;
    }

    /* 발언 제출 (SSE)
     *  - produces = text/event-stream : 응답이 SSE 형식이라는 뜻
     *  - 반환 타입 SseEmitter : 이 메서드가 끝나도 응답은 열린 채로 남고, 서비스가 complete() 할 때 끝난다
     *  - 검사 실패(400 · 403 · 404)는 SSE 를 열기 전이라 평소처럼 JSON 오류 응답이 나간다
     * */
    @Operation(summary = "발언 제출 (SSE)", description = """
            내 발언을 보내면 판정과 AI 상대 발언을 SSE(text/event-stream)로 조금씩 돌려줍니다.
            이벤트 순서 : judge → opponent_delta(여러 번) → opponent_judge → stage(단계가 바뀔 때만) → turn_done
            실패하면 error 이벤트 하나로 끝나고, 그 발언은 반영되지 않습니다. 같은 발언을 다시 보내면 됩니다.
            Swagger 화면은 스트림을 다 받은 뒤에 한꺼번에 보여 주므로, 조각이 흘러나오는 모습은 curl -N 으로 확인합니다.
            """)
    @PostMapping(value = "/api/trials/{trialId}/speakings", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String trialId,
            @RequestBody SpeakingRequestDTO request
    ) {
        return speakingService.submit(user.getId(), trialId, request);
    }
}