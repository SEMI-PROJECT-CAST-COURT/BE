package com.verdict.castcourt.global.websocket;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.security.CustomUserDetails;
import com.verdict.castcourt.global.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/* WebSocket 공통 핸들러 : 기능별 핸들러가 상속해서 쓴다
 *  공통으로 처리하는 것
 *  - 첫 메시지 auth { token } 로 JWT 검증 · 권한 확인 (실패하면 1008 로 닫는다)
 *  - 받은 JSON 의 type 으로 나누기, cancel { } 처리
 *  - 보내기 : delta { text } · done { json } · error { status, code, message }
 *  - AI 작업을 작업 스레드에서 실행 (한 연결에 한 번에 하나), 중단 · 연결 끊김 시 멈추기
 *  기능별 핸들러가 정하는 것 : 주소(path), 허용 권한(allowedAuthorities), type 별 처리(handleMessage)
 * */
@Slf4j
public abstract class BaseWebSocketHandler extends TextWebSocketHandler {

    // 연결마다 따로 두는 값의 이름 (WebSocketSession.getAttributes() 에 넣는다)
    private static final String USER = "user";
    private static final String RUNNING = "running";
    private static final String CANCELLED = "cancelled";

    // 한 번 보내는 데 10초, 보내지 못하고 쌓아 둘 수 있는 양 512KB. 넘으면 느린 연결로 보고 끊는다
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    // 형식이 틀린 메시지에 쓸 오류 코드 (기능마다 다르다. 예 : WS_DRAFT_REQUEST_INVALID)
    private final ErrorCode invalidRequestCode;

    // 열려 있는 연결 (세션 아이디 → 차례로 보내기로 감싼 세션)
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    protected BaseWebSocketHandler(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper,
                                   TaskExecutor taskExecutor, ErrorCode invalidRequestCode) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.invalidRequestCode = invalidRequestCode;
    }

    /* 이 핸들러가 맡을 주소 (예 : /ws/admin/case-draft). WebSocketConfig 가 읽어서 등록한다 */
    public abstract String path();

    /* 연결을 허용할 권한 (예 : ADMIN, SUPER_ADMIN) */
    protected abstract Set<String> allowedAuthorities();

    /* auth · cancel 을 뺀 메시지를 처리한다. 인증된 연결의 메시지만 들어온다 */
    protected abstract void handleMessage(WebSocketSession session, CustomUserDetails user,
                                          String type, JsonNode message);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 작업 스레드(delta)와 메시지 받는 스레드(error)가 동시에 보낼 수 있어서 차례로 보내게 감싼다
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
        session.getAttributes().put(RUNNING, new AtomicBoolean(false));
        session.getAttributes().put(CANCELLED, new AtomicBoolean(false));
        log.info("[WebSocket] 연결 : {} {}", path(), session.getId());
    }

    /* 한 연결의 메시지는 한 번에 하나씩 차례로 들어온다. 오래 걸리는 일은 runAsync 로 넘겨 바로 돌아온다 */
    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage textMessage) {

        WebSocketSession session = sessions.get(rawSession.getId());

        JsonNode message;
        try {
            message = objectMapper.readTree(textMessage.getPayload());
        } catch (RuntimeException e) {
            sendError(session, invalidRequestCode);
            return;
        }
        String type = message.path("type").asString("");

        // 인증 전에는 auth 만 받는다
        CustomUserDetails user = (CustomUserDetails) session.getAttributes().get(USER);
        if (user == null) {
            if (type.equals("auth")) {
                authenticate(session, message.path("token").asString(""));
            } else {
                closeUnauthorized(session);
            }
            return;
        }

        switch (type) {
            // 인증은 한 번만 한다. 다시 오면 무시한다
            case "auth" -> { }
            case "cancel" -> cancel(session);
            default -> {
                try {
                    handleMessage(session, user, type, message);
                } catch (BusinessException e) {
                    // 요청값 검사 실패 등. 연결은 유지한다
                    sendError(session, e.getErrorCode());
                }
            }
        }
    }

    /* 첫 메시지 auth : 토큰 검증 + 권한 확인 */
    private void authenticate(WebSocketSession session, String token) {
        try {
            CustomUserDetails user = jwtTokenProvider.parseAccessToken(token);
            if (!allowedAuthorities().contains(user.getAuthority())) {
                closeUnauthorized(session);
                return;
            }
            session.getAttributes().put(USER, user);
            log.info("[WebSocket] 인증 : {} userId={}", path(), user.getId());
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 오류 · 만료 · 빈 토큰
            closeUnauthorized(session);
        }
    }

    /* 깃발만 세운다. 작업 스레드가 다음 조각을 보내기 전에 보고 스스로 멈춘다 */
    private void cancel(WebSocketSession session) {
        if (flag(session, RUNNING).get()) {
            flag(session, CANCELLED).set(true);
        }
    }

    /* AI 작업을 작업 스레드에서 실행한다. 끝나면 돌려준 값을 done { json } 으로 보낸다
     *  한 연결에서 이미 실행 중이면 invalidRequestCode 로 알리고 실행하지 않는다.
     * */
    protected void runAsync(WebSocketSession session, AiJob job) {

        AtomicBoolean running = flag(session, RUNNING);
        // false 일 때만 true 로 바꾼다 (이미 실행 중이면 실패)
        if (!running.compareAndSet(false, true)) {
            sendError(session, invalidRequestCode);
            return;
        }
        flag(session, CANCELLED).set(false);

        taskExecutor.execute(() -> {
            try {
                Object json = job.run();
                sendDone(session, json);
            } catch (CancelledException e) {
                // cancel 을 받았거나 연결이 끊겼다. 따로 보내지 않는다
                log.info("[WebSocket] 중단 : {} {}", path(), session.getId());
            } catch (BusinessException e) {
                // OpenAiClient 의 AI_UPSTREAM_ERROR · AI_TIMEOUT · AI_CONFIGURATION_MISSING 등
                sendError(session, e.getErrorCode());
            } catch (RuntimeException e) {
                log.error("[WebSocket] 실패 : {}", path(), e);
                sendError(session, ErrorCode.INTERNAL_SERVER_ERROR);
            } finally {
                running.set(false);
            }
        });
    }

    /* 글자 조각을 보낸다. 중단 · 연결 끊김이면 예외로 OpenAiClient.streamText 읽기를 멈춘다 */
    protected void sendDelta(WebSocketSession session, String text) {
        if (flag(session, CANCELLED).get() || !session.isOpen()) {
            throw new CancelledException();
        }
        Map<String, Object> payload = payload("delta");
        payload.put("text", text);
        send(session, payload);
    }

    protected void sendDone(WebSocketSession session, Object json) {
        Map<String, Object> payload = payload("done");
        payload.put("json", json);
        send(session, payload);
    }

    /* error { status, code, message } : 연결은 유지한다 */
    protected void sendError(WebSocketSession session, ErrorCode errorCode) {
        Map<String, Object> payload = payload("error");
        payload.put("status", errorCode.getStatus().value());
        payload.put("code", errorCode.getCode());
        payload.put("message", errorCode.getMessage());
        send(session, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 실행 중인 작업을 멈추게 한다
        if (session.getAttributes().get(CANCELLED) instanceof AtomicBoolean cancelled) {
            cancelled.set(true);
        }
        sessions.remove(session.getId());
        log.info("[WebSocket] 닫힘 : {} {} ({})", path(), session.getId(), status.getCode());
    }

    /* JSON 으로 바꿔 보낸다. 이미 닫힌 연결이면 넘어간다 */
    private void send(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            log.warn("[WebSocket] 보내기 실패 : {}", e.getMessage());
        }
    }

    /* 1008(POLICY_VIOLATION) 로 닫는다. reason 은 WS_AUTHENTICATION_FAILED 메시지 */
    private void closeUnauthorized(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(ErrorCode.WS_AUTHENTICATION_FAILED.getMessage()));
        } catch (IOException ignored) {
            // 이미 닫혔다
        }
    }

    /* type 이 맨 앞에 오는 메시지 (넣은 순서대로 JSON 이 된다) */
    private Map<String, Object> payload(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        return payload;
    }

    private AtomicBoolean flag(WebSocketSession session, String name) {
        return (AtomicBoolean) session.getAttributes().get(name);
    }

    /* AI 작업 : 끝나면 done 의 json 에 담을 값을 돌려준다 */
    @FunctionalInterface
    protected interface AiJob {
        Object run();
    }

    /* 중단 표시용 예외. UncheckedIOException 이라 OpenAiClient.streamText 를 그대로 빠져나온다 */
    private static class CancelledException extends UncheckedIOException {
        CancelledException() {
            super(new IOException("WebSocket 작업 중단"));
        }
    }
}
