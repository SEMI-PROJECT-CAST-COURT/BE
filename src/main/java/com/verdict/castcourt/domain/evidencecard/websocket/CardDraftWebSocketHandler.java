package com.verdict.castcourt.domain.evidencecard.websocket;

import com.verdict.castcourt.domain.evidencecard.dto.CardDraftRequestDTO;
import com.verdict.castcourt.domain.evidencecard.service.CardDraftService;
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

/* AI 사건 초안 (노션 API 명세 'AI 사건 초안') : ws://localhost:8080/ws/admin/case-draft
 *  → auth { token }  : BaseWebSocketHandler 가 처리
 *  → draft { step, ... } : 여기서 step 으로 나눈다
 *  → cancel { }      : BaseWebSocketHandler 가 처리
 *  ← delta { text } / done { json } / error { status, code, message }
 *  주소 등록(WebSocketConfig) · /ws/** 허용(SecurityConfig)은 이미 되어 있어서 이 클래스만 추가하면 된다.
 * */
@Component
public class CardDraftWebSocketHandler extends BaseWebSocketHandler {

    private final CardDraftService cardDraftService;

    public CardDraftWebSocketHandler(JwtTokenProvider jwtTokenProvider,
                                     ObjectMapper objectMapper,
                                     // 스프링 부트가 만들어 둔 비동기 작업용 실행기 (SpeakingService 와 같은 것)
                                     @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                     CardDraftService cardDraftService) {
        // 형식이 틀린 메시지 · 이미 생성 중일 때 보낼 오류 코드 = WS_DRAFT_REQUEST_INVALID (명세)
        super(jwtTokenProvider, objectMapper, taskExecutor, ErrorCode.WS_DRAFT_REQUEST_INVALID);
        this.cardDraftService = cardDraftService;
    }

    @Override
    public String path() {
        return "/ws/admin/evidence-card-draft";
    }

    @Override
    protected Set<String> allowedAuthorities() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    /* draft 만 받는다. 여기서 던진 BusinessException 은 BaseWebSocketHandler 가 error 로 보내고 연결은 유지한다 */
    @Override
    protected void handleMessage(WebSocketSession session, CustomUserDetails user,
                                 String type, JsonNode message) {

        if (!"draft".equals(type)) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }

        String step = message.path("step").asString("");
        switch (step) {
            // 근거 카드 : 요청 한 번에 1장 (side 로 진영 지정)
            case "CARDS" -> {
                CardDraftRequestDTO request = CardDraftRequestDTO.from(message);
                // 요청값 · 작품 확인은 작업 스레드로 넘기기 전에 한다 (실패하면 바로 error)
                cardDraftService.validate(request);
                // AI 호출은 작업 스레드에서 · 돌려준 카드 1장이 done 의 json 이 된다
                runAsync(session, () -> cardDraftService.generate(request));
            }
            // TODO CANDIDATES / REWRITE / CARD_SUGGEST / PENALTY
            default -> throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
    }
}
