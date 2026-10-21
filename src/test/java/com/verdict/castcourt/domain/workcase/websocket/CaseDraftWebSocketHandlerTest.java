package com.verdict.castcourt.domain.workcase.websocket;

import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.domain.workcase.service.CaseDraftService;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.security.CustomUserDetails;
import com.verdict.castcourt.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/* 사건 초안 WebSocket : 인증 · type · step 나누기 · 요청값 검사 · 응답 프레임을 확인한다
 *  작업 실행기는 SyncTaskExecutor 로 바꿔 runAsync 가 그 자리에서 끝나게 한다.
 * */
@ExtendWith(MockitoExtension.class)
class CaseDraftWebSocketHandlerTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CaseDraftService caseDraftService;

    @Mock
    private WebSocketSession session;

    private CaseDraftWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CaseDraftWebSocketHandler(jwtTokenProvider, jsonMapper, new SyncTaskExecutor(), caseDraftService);

        lenient().when(session.getId()).thenReturn("session-1");
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(jwtTokenProvider.parseAccessToken(ADMIN_TOKEN))
                .thenReturn(new CustomUserDetails(1, "admin", "", "ADMIN"));
        lenient().when(jwtTokenProvider.parseAccessToken(USER_TOKEN))
                .thenReturn(new CustomUserDetails(2, "user", "", "USER"));

        handler.afterConnectionEstablished(session);
    }

    @Test
    @DisplayName("auth 전에 다른 메시지가 오면 1008 로 닫는다")
    void notAuthenticated() throws Exception {
        receive("{\"type\":\"hello\"}");

        assertClosedWithPolicyViolation();
    }

    @Test
    @DisplayName("ADMIN · SUPER_ADMIN 이 아니면 1008 로 닫는다")
    void notAdmin() throws Exception {
        receive("{\"type\":\"auth\",\"token\":\"" + USER_TOKEN + "\"}");

        assertClosedWithPolicyViolation();
    }

    @Test
    @DisplayName("hello : done { connected: true }")
    void hello() throws Exception {
        authenticate();

        receive("{\"type\":\"hello\"}");

        JsonNode sent = lastSent();
        assertEquals("done", sent.path("type").asString());
        assertTrue(sent.path("json").path("connected").asBoolean());
    }

    @Test
    @DisplayName("CANDIDATES : excludeQuestions 를 넘기고 후보 배열을 done 으로 보낸다")
    void candidates() throws Exception {
        authenticate();
        when(caseDraftService.candidates(1, List.of("A 는 유죄인가?"))).thenReturn(List.of(
                new CaseDraftCandidateResponseDTO("쟁점", "피의자", "피해자", "개요", "경과")));

        receive("""
                {"type":"draft","step":"CANDIDATES","workId":1,"excludeQuestions":["A 는 유죄인가?"]}
                """);

        JsonNode sent = lastSent();
        assertEquals("done", sent.path("type").asString());
        assertTrue(sent.path("json").isArray());
        assertEquals("쟁점", sent.path("json").get(0).path("question").asString());
        assertEquals("경과", sent.path("json").get(0).path("progress").asString());
    }

    @Test
    @DisplayName("CANDIDATES : excludeQuestions 가 없으면 빈 목록으로 부른다 (처음 받기)")
    void candidatesWithoutExclude() throws Exception {
        authenticate();
        when(caseDraftService.candidates(1, List.of())).thenReturn(List.of());

        receive("{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":1}");

        verify(caseDraftService).candidates(1, List.of());
        assertEquals("done", lastSent().path("type").asString());
    }

    @Test
    @DisplayName("REWRITE : 요청값을 DTO 로 넘기고 개요 · 경과를 done 으로 보낸다")
    void rewrite() throws Exception {
        authenticate();
        CaseDraftRequestDTO request = new CaseDraftRequestDTO(
                "쟁점", "피의자", "피해자", "옛 개요", "옛 경과", "경과를 더 짧게");
        when(caseDraftService.rewrite(1, request)).thenReturn(new CaseDraftRewriteResponseDTO("새 개요", "새 경과"));

        receive("""
                {"type":"draft","step":"REWRITE","workId":1,"question":"쟁점","suspect":"피의자","victim":"피해자",
                 "summary":"옛 개요","progress":"옛 경과","feedback":"경과를 더 짧게"}
                """);

        JsonNode sent = lastSent();
        assertEquals("done", sent.path("type").asString());
        assertEquals("새 개요", sent.path("json").path("summary").asString());
        assertEquals("새 경과", sent.path("json").path("progress").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"unknown\"}",                                                            // 모르는 type
            "{\"type\":\"draft\",\"step\":\"CANDIDATES\"}",                                      // workId 없음
            "{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":\"1\"}",                     // workId 문자열
            "{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":0}",                         // workId 0
            "{\"type\":\"draft\",\"step\":\"CARDS\",\"workId\":1}",                              // 맡지 않는 step
            "{\"type\":\"draft\",\"workId\":1}",                                                 // step 없음
            "{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":1,\"excludeQuestions\":\"A\"}",   // 배열 아님
            "{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":1,\"excludeQuestions\":[1]}",     // 문자열 아님
            "{\"type\":\"draft\",\"step\":\"REWRITE\",\"workId\":1,\"question\":\"q\",\"suspect\":\"s\",\"victim\":\"v\",\"summary\":\"m\",\"progress\":\"p\"}",                 // feedback 없음
            "{\"type\":\"draft\",\"step\":\"REWRITE\",\"workId\":1,\"question\":\"q\",\"suspect\":\"s\",\"victim\":\"v\",\"summary\":\"m\",\"progress\":\"p\",\"feedback\":\" \"}", // feedback 공백
            "not json"                                                                           // JSON 아님
    })
    @DisplayName("요청값이 틀리면 error 400 WS_DRAFT_REQUEST_INVALID, 연결은 유지하고 AI 는 부르지 않는다")
    void invalidRequest(String payload) throws Exception {
        authenticate();

        receive(payload);

        assertError(lastSent(), ErrorCode.WS_DRAFT_REQUEST_INVALID);
        verifyNoInteractions(caseDraftService);
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("서비스 오류(작품 없음)는 error 프레임으로 보내고 연결은 유지한다")
    void serviceError() throws Exception {
        authenticate();
        when(caseDraftService.candidates(anyInt(), any()))
                .thenThrow(new BusinessException(ErrorCode.WORK_NOT_FOUND));

        receive("{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":99}");

        assertError(lastSent(), ErrorCode.WORK_NOT_FOUND);
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("AI 오류(AI_TIMEOUT)는 error 504 로 보낸다")
    void aiError() throws Exception {
        authenticate();
        when(caseDraftService.candidates(anyInt(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

        receive("{\"type\":\"draft\",\"step\":\"CANDIDATES\",\"workId\":1}");

        assertError(lastSent(), ErrorCode.AI_TIMEOUT);
    }

    private void authenticate() throws Exception {
        receive("{\"type\":\"auth\",\"token\":\"" + ADMIN_TOKEN + "\"}");
    }

    private void receive(String payload) throws Exception {
        handler.handleMessage(session, new TextMessage(payload));
    }

    /* 마지막으로 보낸 프레임 */
    private JsonNode lastSent() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        WebSocketMessage<?> last = captor.getValue();
        return jsonMapper.readTree(((TextMessage) last).getPayload());
    }

    private void assertError(JsonNode sent, ErrorCode errorCode) {
        assertEquals("error", sent.path("type").asString());
        assertEquals(errorCode.getStatus().value(), sent.path("status").asInt());
        assertEquals(errorCode.getCode(), sent.path("code").asString());
    }

    private void assertClosedWithPolicyViolation() throws Exception {
        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertEquals(CloseStatus.POLICY_VIOLATION.getCode(), status.getValue().getCode());
    }
}
