package com.verdict.castcourt.domain.speaking.ai;

import com.sun.net.httpserver.HttpServer;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/* OpenAI 대신 이 테스트 안에 띄운 작은 HTTP 서버에 요청을 보낸다
 *  실제 키 · 비용 없이 응답 읽기, 토큰 기록, 오류 → ErrorCode 바꾸기를 확인한다.
 * */
class OpenAiClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("구조화 출력 : output_text 의 JSON 과 토큰 수를 돌려준다")
    void createJson() throws IOException {
        OpenAiClient client = clientFor(200, "application/json", """
                {"status":"completed",
                 "output":[{"type":"reasoning"},
                           {"type":"message","content":[{"type":"output_text","text":"{\\"issue\\":true}"}]}],
                 "usage":{"input_tokens":120,"output_tokens":7}}
                """);

        AiResult<JsonNode> result = client.createJson("지시", "입력", 200, "test", Map.of());

        assertEquals(true, result.output().path("issue").asBoolean());
        assertEquals(120, result.usage().inputTokens());
        assertEquals(7, result.usage().outputTokens());
    }

    @Test
    @DisplayName("스트리밍 : 글자 조각마다 onDelta 를 부르고 전체 문장을 돌려준다")
    void streamText() throws IOException {
        OpenAiClient client = clientFor(200, "text/event-stream", """
                event: response.created
                data: {"type":"response.created"}

                data: {"type":"response.output_text.delta","delta":"재판장님, "}

                data: {"type":"response.output_text.delta","delta":"입니다."}

                data: {"type":"response.completed","response":{"usage":{"input_tokens":50,"output_tokens":4}}}

                """);
        List<String> deltas = new ArrayList<>();

        AiResult<String> result = client.streamText("지시", "입력", 600, deltas::add);

        assertEquals(List.of("재판장님, ", "입니다."), deltas);
        assertEquals("재판장님, 입니다.", result.output());
        assertEquals(4, result.usage().outputTokens());
    }

    @Test
    @DisplayName("401 은 설정 문제(503), 그 밖 오류 · 끊긴 응답은 502, 키가 없으면 호출 전에 503")
    void errors() throws IOException {
        assertError(ErrorCode.AI_CONFIGURATION_MISSING,
                clientFor(401, "application/json", "{\"error\":{\"message\":\"bad key\"}}"));
        stop();
        assertError(ErrorCode.AI_UPSTREAM_ERROR,
                clientFor(429, "application/json", "{\"error\":{\"message\":\"quota\"}}"));
        stop();
        assertError(ErrorCode.AI_UPSTREAM_ERROR,
                clientFor(200, "application/json", "{\"status\":\"incomplete\",\"output\":[],\"usage\":{}}"));

        OpenAiClient noKey = new OpenAiClient(JsonMapper.builder().build(), "", "http://localhost:1", "m", "");
        assertError(ErrorCode.AI_CONFIGURATION_MISSING, noKey);
    }

    private void assertError(ErrorCode expected, OpenAiClient client) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> client.createJson("지시", "입력", 200, "test", Map.of()));
        assertEquals(expected, e.getErrorCode());
    }

    // /responses 에 정해진 상태 코드 · 본문으로 답하는 서버를 띄우고, 그 주소를 쓰는 클라이언트를 만든다
    private OpenAiClient clientFor(int status, String contentType, String body) throws IOException {

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);   // 0 = 빈 포트 아무거나
        server.createContext("/v1/responses", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new OpenAiClient(JsonMapper.builder().build(), "test-key", baseUrl, "test-model", "");
    }
}