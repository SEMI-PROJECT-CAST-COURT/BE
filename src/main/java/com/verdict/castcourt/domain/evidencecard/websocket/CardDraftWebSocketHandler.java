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
                cardDraftService.validateDraft(request);
                // AI 호출은 작업 스레드에서 · 돌려준 카드 1장이 done 의 json 이 된다
                runAsync(session, () -> cardDraftService.generate(request));
            }
            // 근거 카드 수정 제안 : card(검토할 카드) → 제안 문구 + 고친 카드 ([적용] / [무시]는 화면이 정한다)
            case "CARD_SUGGEST" -> {
                CardDraftRequestDTO request = CardDraftRequestDTO.from(message);
                cardDraftService.validateSuggest(request);
                runAsync(session, () -> cardDraftService.suggest(request));
            }
            // TODO CANDIDATES / REWRITE / PENALTY
            default -> throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
    }
}
