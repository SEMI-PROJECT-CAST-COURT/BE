package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.speaking.ai.model.OpenAiTopicAi;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiTopicAiTest {
    private final OpenAiClient client = mock(OpenAiClient.class);

    @Test
    void mapsOnlyRequestedCards() {
        given("{\"topics\":[{\"cardId\":12,\"topic\":\"행동의 의도\"},{\"cardId\":11,\"topic\":\" 현장 존재 여부 \"}]}");
        Map<Integer, String> topics = new OpenAiTopicAi(client).generate(
                SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11, 12));
        assertEquals(Map.of(11, "현장 존재 여부", 12, "행동의 의도"), topics);
        verify(client).createJson(anyString(), argThat(input -> input.contains("검사 카드 1")
                && !input.contains("변호인 카드")), eq(800), eq("trial_topics"), any());
        assertThrows(UnsupportedOperationException.class, () -> topics.put(99, "다른 논점"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsIncompleteOrInvalidTopics(String json) {
        given(json);
        BusinessException error = assertThrows(BusinessException.class, () -> new OpenAiTopicAi(client).generate(
                SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11, 12)));
        assertEquals(ErrorCode.AI_UPSTREAM_ERROR, error.getErrorCode());
    }

    static Stream<String> invalidResponses() {
        return Stream.of("{}", "{\"topics\":[]}",
                "{\"topics\":[{\"cardId\":11,\"topic\":\"주제\"}]}",
                "{\"topics\":[{\"cardId\":11,\"topic\":\"주제\"},{\"cardId\":11,\"topic\":\"중복\"}]}",
                "{\"topics\":[{\"cardId\":11,\"topic\":\"주제\"},{\"cardId\":99,\"topic\":\"미요청\"}]}",
                "{\"topics\":[{\"cardId\":11,\"topic\":\"주제\"},{\"cardId\":12,\"topic\":\" \"}]}",
                "{\"topics\":[{\"cardId\":11,\"topic\":\"주제\"},{\"cardId\":12,\"topic\":\"" + "가".repeat(61) + "\"}]}");
    }

    private void given(String json) {
        when(client.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new AiResult<>(JsonMapper.builder().build().readTree(json), null));
    }
}
