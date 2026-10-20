package com.verdict.castcourt.domain.workcase.websocket;

import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.service.CaseDraftService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* AI 사건 초안 (관리자 사건 등록 화면)
 *  주소 : ws://localhost:8080/ws/admin/case-draft
 *  auth · cancel 은 BaseWebSocketHandler 가 처리하고, 여기서는 아래 type 만 다룬다
 *  - hello { }                                       → done { connected: true }  ("AI 초안 연결됨" 표시용)
 *  - draft { step, workId, ... } : step 별로 나눈다
 *      CANDIDATES { excludeQuestions[] }              → done [ { question, suspect, victim, summary, progress } ] x 3
 *                                                       (다시 받기 : 앞 후보의 question 을 excludeQuestions 에 담아 보낸다)
 *      REWRITE { question, suspect, victim, summary, progress, feedback } → done { summary, progress }
 *  요청값이 틀리면 error(WS_DRAFT_REQUEST_INVALID) 를 보내고 연결은 유지한다
 * */
@Component
public class CaseDraftWebSocketHandler extends BaseWebSocketHandler {

    private final CaseDraftService caseDraftService;

    public CaseDraftWebSocketHandler(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper,
                                     @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                     CaseDraftService caseDraftService) {
        super(jwtTokenProvider, objectMapper, taskExecutor, ErrorCode.WS_DRAFT_REQUEST_INVALID);
        this.caseDraftService = caseDraftService;
    }

    @Override
    public String path() {
        return "/ws/admin/case-draft";
    }

    @Override
    protected Set<String> allowedAuthorities() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    @Override
    protected void handleMessage(WebSocketSession session, CustomUserDetails user,
                                 String type, JsonNode message) {

        switch (type) {
            case "hello" -> sendDone(session, Map.of("connected", true));
            case "draft" -> draft(session, message);
            default -> throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
    }

    /* draft : 모든 step 이 workId 를 받는다 */
    private void draft(WebSocketSession session, JsonNode message) {

        int workId = requireWorkId(message);

        switch (message.path("step").asString("")) {
            case "CANDIDATES" -> candidates(session, workId, message);  // 쟁점 후보
            case "REWRITE" -> rewrite(session, workId, message);        // 사건 개요, 경과 다시 쓰기
            default -> throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
    }

    /* 쟁점 후보 3개 (각각 피의자 · 피해자 · 개요 · 경과까지). 다시 받기면 excludeQuestions 는 빼고 만든다 */
    private void candidates(WebSocketSession session, int workId, JsonNode message) {

        List<String> excludeQuestions = optionalStrings(message, "excludeQuestions");

        runAsync(session, () -> caseDraftService.candidates(workId, excludeQuestions));
    }

    /* 고칠 점(feedback)을 반영해 개요 · 경과 다시 쓰기 */
    private void rewrite(WebSocketSession session, int workId, JsonNode message) {

        String question = requireText(message, "question");
        String suspect = requireText(message, "suspect");
        String victim = requireText(message, "victim");
        String summary = requireText(message, "summary");
        String progress = requireText(message, "progress");
        String feedback = requireText(message, "feedback");

        CaseDraftRequestDTO requestDTO = new CaseDraftRequestDTO(
                question, suspect, victim, summary, progress, feedback
        );

        runAsync(session, () -> caseDraftService.rewrite(workId, requestDTO));
    }

    /* workId : 1 이상의 정수 */
    private int requireWorkId(JsonNode message) {
        JsonNode node = message.path("workId");
        if (!node.isInt() || node.asInt() < 1) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        return node.asInt();
    }

    /* 비어 있지 않은 문자열 */
    private String requireText(JsonNode message, String field) {
        JsonNode node = message.path(field);
        if (!node.isString() || node.asString().isBlank()) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        return node.asString().strip();
    }

    /* 문자열 배열 (없으면 빈 목록) */
    private List<String> optionalStrings(JsonNode message, String field) {
        JsonNode node = message.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
            }
            values.add(item.asString());
        }
        return values;
    }
}
