package com.verdict.castcourt.domain.penaltytier.websocket;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyDraftRequestDTO;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyDraftService;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.security.CustomUserDetails;
import com.verdict.castcourt.global.security.JwtTokenProvider;
import com.verdict.castcourt.global.websocket.BaseWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
public class PenaltyDraftWebSocketHandler extends BaseWebSocketHandler {
    private final PenaltyDraftService penaltyDraftService;

    public PenaltyDraftWebSocketHandler(JwtTokenProvider jwtTokenProvider,
                                        ObjectMapper objectMapper,
                                        // 스프링 부트가 만들어 둔 비동기 작업용 실행기 (SpeakingService 와 같은 것)
                                        @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                        PenaltyDraftService penaltyDraftService) {
        // 형식이 틀린 메시지 · 이미 생성 중일 때 보낼 오류 코드 = WS_DRAFT_REQUEST_INVALID (명세)
        super(jwtTokenProvider, objectMapper, taskExecutor, ErrorCode.WS_DRAFT_REQUEST_INVALID);
        this.penaltyDraftService = penaltyDraftService;
    }

    @Override
    public String path() {
        return "/ws/admin/penalty-draft";
    }

    @Override
    protected Set<String> allowedAuthorities() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    /* draft + step PENALTY 만 받는다. 여기서 던진 BusinessException 은 BaseWebSocketHandler 가 error 로 보내고 연결은 유지한다 */
    @Override
    protected void handleMessage(WebSocketSession session, CustomUserDetails user,
                                 String type, JsonNode message) {

        if (!"draft".equals(type) || !"PENALTY".equals(message.path("step").asString(""))) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }

        PenaltyDraftRequestDTO request = PenaltyDraftRequestDTO.from(message);
        // 요청값 · 작품 확인은 작업 스레드로 넘기기 전에 한다 (실패하면 바로 error)
        penaltyDraftService.validate(request);
        // AI 호출은 작업 스레드에서 · 돌려준 6단계 목록이 done 의 json 이 된다
        runAsync(session, () -> penaltyDraftService.generate(request));
    }
}
