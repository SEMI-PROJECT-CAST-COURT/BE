package com.verdict.castcourt.global.ai;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.metrics.CastCourtMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/* OpenAI Responses API 를 부르는 부품 (예제 phase-4 OpenAiClient 를 이 프로젝트에 맞춘 것)
 *      POST {base-url}/responses
 *      Authorization: Bearer {API 키}
 *      { "model", "instructions", "input", "max_output_tokens", ... }
 *  - createJson() : 응답을 한 번에 받는다. JSON Schema 모양으로만 답하게 한다. (판사 판정 · 총평)
 *  - streamText() : 응답을 조금씩 받는다. 글자 조각마다 onDelta 를 부른다. (상대 발언)
 *  예제와 다른 점
 *  - 실패는 BusinessException(ErrorCode.AI_*) 로 던진다 → GlobalExceptionHandler 가 502 / 503 / 504 로 응답한다
 *  - 결과와 함께 AiUsage(모델 · 토큰 · 시각 · 걸린 시간)를 돌려준다
 *  - 설정값은 @Value 기본값으로 둔다 → 공용 application.yaml 을 고치지 않아도 뜬다
 *  OpenAI 의 요청 · 응답 모양을 아는 곳은 이 클래스뿐이다.
 * */
@Slf4j
@Component
public class OpenAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;          // API 키가 있는지 (없으면 호출할 때 503)
    private final String model;
    private final String reasoningEffort;

    public OpenAiClient(ObjectMapper objectMapper,
                        // 환경 변수 OPENAI_API_KEY → openai.api-key (없으면 빈 문자열)
                        @Value("${openai.api-key}") String apiKey,
                        @Value("${openai.base-url}") String baseUrl,
                        @Value("${openai.model}") String model,
                        // 추론(생각) 정도 · 빈 값이면 보내지 않는다 (추론 기능이 없는 모델용)
                        @Value("${openai.reasoning-effort}") String reasoningEffort) {

        this.objectMapper = objectMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.model = model;
        this.reasoningEffort = reasoningEffort;

        // 연결 5초, 응답 시작까지 60초 · 정하지 않으면 OpenAI 가 멈췄을 때 스레드가 끝없이 기다린다
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        // RestClient.builder() 는 Spring Framework 정적 메서드라 의존성 추가가 필요 없다
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();

        // 키 값은 절대 로그에 찍지 않는다. 있는지만 알린다.
        log.info("[OpenAiClient] OPENAI_API_KEY {} · model={}", enabled ? "확인됨" : "없음 (AI 호출은 503)", model);
    }

    /* 한 번에 받기 : schema 모양의 JSON 을 돌려준다
     *  strict = true 라 스키마에 없는 필드를 만들거나 필수 필드를 빼먹지 않는다.
     *  schemaName(judge_result · trial_topics …)은 지표의 용도(purpose) 태그로도 쓴다.
     * */
    public AiResult<JsonNode> createJson(String instructions, String input, int maxOutputTokens,
                                         String schemaName, Map<String, Object> schema) {
        return measure(schemaName, () -> requestJson(instructions, input, maxOutputTokens, schemaName, schema));
    }

    private AiResult<JsonNode> requestJson(String instructions, String input, int maxOutputTokens,
                                           String schemaName, Map<String, Object> schema) {

        Map<String, Object> body = requestBody(instructions, input, maxOutputTokens);
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", true,
                "schema", schema)));

        LocalDateTime calledAt = LocalDateTime.now();
        long start = System.currentTimeMillis();

        String responseBody = call(() -> restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // 4xx · 5xx 면 우리 예외로 바꿔 던진다
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw toException(response.getStatusCode(), response.getBody());
                })
                .body(String.class));

        JsonNode response = objectMapper.readTree(responseBody);
        AiUsage usage = usage(response, calledAt, start);

        // 출력 토큰 상한에 걸려 끊긴 응답은 JSON 이 잘려 있으므로 쓰지 않는다
        if ("incomplete".equals(response.path("status").asString())) {
            log.warn("[OpenAiClient] 출력 토큰 상한에서 끊김 : max_output_tokens={}", maxOutputTokens);
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }

        // AI 가 쓴 글은 'JSON 이 들어 있는 문자열'이라 한 번 더 읽는다
        return new AiResult<>(objectMapper.readTree(outputText(response)), usage);
    }

    /* 조금씩 받기 : 글자 조각마다 onDelta 를 부르고, 다 받으면 전체 문장을 돌려준다
     *  onDelta 에서 예외가 나면(브라우저 연결 끊김 등) 읽기를 멈추고 그 예외가 그대로 올라간다.
     * */
    public AiResult<String> streamText(String instructions, String input, int maxOutputTokens,
                                       Consumer<String> onDelta) {
        // 스트리밍은 지금 상대 발언만 쓴다 → 용도 태그는 opponent_speech
        return measure(CastCourtMetrics.PURPOSE_OPPONENT_SPEECH,
                () -> requestStream(instructions, input, maxOutputTokens, onDelta));
    }

    private AiResult<String> requestStream(String instructions, String input, int maxOutputTokens,
                                           Consumer<String> onDelta) {

        Map<String, Object> body = requestBody(instructions, input, maxOutputTokens);
        body.put("stream", true);

        LocalDateTime calledAt = LocalDateTime.now();
        long start = System.currentTimeMillis();

        // retrieve() 는 다 받은 뒤에 돌려주므로, 받자마자 본문을 넘겨주는 exchange() 를 쓴다
        return call(() -> restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(body)
                .exchange((request, response) -> {

                    if (response.getStatusCode().isError()) {
                        throw toException(response.getStatusCode(), response.getBody());
                    }

                    StringBuilder text = new StringBuilder();
                    AiUsage usage = null;

                    /* OpenAI 가 보내는 SSE (빈 줄 하나가 이벤트 하나의 끝)
                     *      event: response.output_text.delta
                     *      data: {"type":"response.output_text.delta","delta":"재판장님",...}
                     *  필요한 정보는 data: 줄의 JSON 에 모두 있어서 그 줄만 읽는다.
                     * */
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) {
                                continue;
                            }
                            JsonNode event = objectMapper.readTree(line.substring("data: ".length()));

                            switch (event.path("type").asString()) {
                                // 글자 조각 → 모아 두고 바로 넘긴다
                                case "response.output_text.delta" -> {
                                    String delta = event.path("delta").asString();
                                    text.append(delta);
                                    onDelta.accept(delta);
                                }
                                // 마지막 이벤트에 토큰 사용량이 있다
                                case "response.completed" -> usage = usage(event.path("response"), calledAt, start);
                                // 토큰 상한에 걸려 끊김 · 만드는 도중 실패 → 반쪽짜리 발언은 쓰지 않는다
                                case "response.incomplete", "response.failed", "error" -> {
                                    log.warn("[OpenAiClient] 스트리밍 실패 : {}", event.path("type").asString());
                                    throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
                                }
                                default -> {
                                    // response.created 등 나머지 이벤트는 쓰지 않는다
                                }
                            }
                        }
                    }

                    // completed 없이 연결이 끝났으면 응답이 덜 온 것
                    if (usage == null) {
                        throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
                    }
                    return new AiResult<>(text.toString(), usage);
                }));
    }

    // 키 확인 → 호출 → 타임아웃 · 연결 실패를 우리 예외로 바꾼다 (두 호출 공통)
    private <T> T call(Supplier<T> request) {

        if (!enabled) {
            throw new BusinessException(ErrorCode.AI_CONFIGURATION_MISSING);
        }
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            // 응답 대기 시간 초과 → 504, 그 밖의 연결 실패 → 502
            boolean timeout = e.getCause() instanceof HttpTimeoutException
                    || e.getCause() instanceof SocketTimeoutException;
            log.warn("[OpenAiClient] 연결 실패 : {}", e.getMessage());
            throw new BusinessException(timeout ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_UPSTREAM_ERROR);
        }
    }

    /* 지표 기록 (두 호출 공통) : 호출 한 번의 시간 · 결과 · 토큰을 CastCourtMetrics 에 남긴다
     *  결과(outcome) : SUCCESS / BusinessException 의 코드(AI_UPSTREAM_ERROR · AI_TIMEOUT · AI_CONFIGURATION_MISSING)
     *                  / CANCELLED(스트리밍 도중 브라우저 · WebSocket 이 끊겨 onDelta 가 멈춤) / 그 밖은 INTERNAL_SERVER_ERROR
     *  예외는 바꾸지 않고 그대로 다시 던진다. 토큰은 성공한 호출만 센다.
     * */
    private <T> AiResult<T> measure(String purpose, Supplier<AiResult<T>> request) {

        long start = System.nanoTime();
        String outcome = ErrorCode.INTERNAL_SERVER_ERROR.name();    // 아래에서 성공 · 알려진 실패로 바꾼다
        try {
            AiResult<T> result = request.get();
            outcome = CastCourtMetrics.SUCCESS;
            if (result.usage() != null) {
                CastCourtMetrics.recordAiTokens(purpose, result.usage().inputTokens(), result.usage().outputTokens());
            }
            return result;
        } catch (BusinessException e) {
            outcome = e.getErrorCode().name();
            throw e;
        } catch (UncheckedIOException e) {
            outcome = CastCourtMetrics.CANCELLED;
            throw e;
        } finally {
            CastCourtMetrics.recordAiRequest(purpose, outcome, System.nanoTime() - start);
        }
    }

    // 두 호출이 함께 쓰는 요청 본문
    private Map<String, Object> requestBody(String instructions, String input, int maxOutputTokens) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", instructions);                         // AI 의 역할과 규칙
        body.put("input", List.of(Map.of("role", "user", "content", input)));  // 사건 · 발언 내용
        body.put("max_output_tokens", maxOutputTokens);                 // 출력 토큰 상한 = 비용 상한
        body.put("store", false);                                       // OpenAI 서버에 대화를 저장하지 않는다
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning", Map.of("effort", reasoningEffort));
        }
        return body;
    }

    // output 배열에서 type = message → content 의 type = output_text 글만 이어 붙인다
    private String outputText(JsonNode response) {

        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if ("message".equals(item.path("type").asString())) {
                for (JsonNode content : item.path("content")) {
                    if ("output_text".equals(content.path("type").asString())) {
                        text.append(content.path("text").asString());
                    }
                }
            }
        }
        return text.toString();
    }

    // 응답의 usage 로 사용량을 만들고 콘솔에 찍는다
    private AiUsage usage(JsonNode response, LocalDateTime calledAt, long start) {

        JsonNode usage = response.path("usage");
        AiUsage aiUsage = new AiUsage(
                model,
                usage.path("input_tokens").asInt(),
                usage.path("output_tokens").asInt(),
                calledAt,
                System.currentTimeMillis() - start
        );
        log.info("[OpenAiClient] 완료 : model={}, 입력 토큰={}, 출력 토큰={}, {}ms",
                aiUsage.model(), aiUsage.inputTokens(), aiUsage.outputTokens(), aiUsage.elapsedMs());
        return aiUsage;
    }

    /* OpenAI 오류 응답 → 우리 예외
     *  오류 본문 : { "error": { "message": "...", "type": "...", "code": "..." } }
     *  - 401 : 키가 틀림 = 우리 설정 문제 → 503 (우리 서비스 로그인과 관계없으니 401 로 넘기지 않는다)
     *  - 그 밖 (403 권한 · 429 한도/잔액 · 5xx) : OpenAI 쪽 실패 → 502
     * */
    private BusinessException toException(HttpStatusCode status, InputStream errorBody) throws IOException {

        String body = new String(errorBody.readAllBytes(), StandardCharsets.UTF_8);
        String message;
        try {
            message = objectMapper.readTree(body).path("error").path("message").asString(body);
        } catch (RuntimeException e) {
            message = body;     // JSON 이 아니면 본문 그대로
        }
        // 원인을 찾을 수 있게 상태 코드와 메시지는 서버 로그에만 남긴다 (사용자에게는 ErrorCode 문구)
        log.warn("[OpenAiClient] 실패 : HTTP {} - {}", status.value(), message);

        return new BusinessException(status.value() == 401
                ? ErrorCode.AI_CONFIGURATION_MISSING
                : ErrorCode.AI_UPSTREAM_ERROR);
    }
}
