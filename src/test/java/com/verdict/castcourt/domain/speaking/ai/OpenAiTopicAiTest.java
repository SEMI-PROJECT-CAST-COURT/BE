package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.speaking.ai.model.OpenAiTopicAi;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialTopic;
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
        given(response(entry(11, " 현장 존재 여부 ", "21")));
        Map<Integer, TrialTopic> topics = new OpenAiTopicAi(client).generate(
                SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11));
        assertEquals(Map.of(11, new TrialTopic("현장 존재 여부", 21)), topics);
        verify(client).createJson(anyString(), argThat(input -> input.contains("검사 카드 1")
                && !input.contains("검사 카드 2") && input.contains("변호인 카드")
                && input.contains("소속: DEFENSE")), eq(800), eq("trial_topics"), any());
        assertThrows(UnsupportedOperationException.class, () -> topics.put(99, new TrialTopic("다른 논점", null)));
    }

    @Test
    void allowsNoDirectCounterCardWithoutChoosingAnUnrelatedOne() {
        given(response(entry(11, "현장 존재 여부", "null")));
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.DEFENSE).getSnapshot(), List.of(11));
        assertNull(topics.get(11).defenseCardId());
    }

    @Test
    void allowsOneDefenseCardToSupportMultipleRelevantTopics() {
        given(response(entry(12, "행동의 의도", "21"), entry(11, "현장 존재 여부", "21")));
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.DEFENSE).getSnapshot(), List.of(11, 12));
        assertEquals(21, topics.get(11).defenseCardId());
        assertEquals(21, topics.get(12).defenseCardId());
    }

    @ParameterizedTest
    @MethodSource("invalidItems")
    void replacesOnlyInvalidItemsWithCardTitle(String json) {
        given(json);
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11, 12));
        assertEquals(new TrialTopic("주제", 21), topics.get(11));
        assertEquals(new TrialTopic("검사 카드 2", null), topics.get(12));
        verify(client, times(1)).createJson(anyString(), anyString(), anyInt(), anyString(), any());
    }

    static Stream<String> invalidItems() {
        String first = entry(11, "주제", "21");
        return Stream.of(response(first),
                response(first, entry(11, "중복", "21")),
                response(first, entry(99, "미요청", "21")),
                response(first, entry(12, " ", "21")),
                response(first, entry(12, "가".repeat(61), "21")),
                response(first, entry(12, "주제", "11")),
                response(first, entry(12, "주제", "99")),
                response(first, entry(12, "주제", "\"21\"")),
                response(first, entry(12, "주제", "21.5")),
                response(first, entry(12, "주제", "true")),
                response(first, "{\"cardId\":12,\"topic\":\"주제\"}"),
                response(first, "{\"cardId\":\"12\",\"topic\":\"주제\",\"defenseCardId\":21}"),
                response(first, "{\"cardId\":12,\"topic\":null,\"defenseCardId\":21}"));
    }

    @ParameterizedTest
    @MethodSource("unusableResponses")
    void retriesOnceThenUsesCardTitles(String json) {
        given(json);
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11, 12));
        assertEquals(Map.of(11, new TrialTopic("검사 카드 1", null), 12, new TrialTopic("검사 카드 2", null)), topics);
        verify(client, times(2)).createJson(anyString(), anyString(), anyInt(), anyString(), any());
    }

    static Stream<String> unusableResponses() {
        return Stream.of("{}", "{\"topics\":{}}");
    }

    @Test
    void retriesAfterUpstreamFailure() {
        when(client.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_UPSTREAM_ERROR))
                .thenReturn(new AiResult<>(JsonMapper.builder().build().readTree(response(entry(11, "현장 존재 여부", "21"))), null));
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11));
        assertEquals(new TrialTopic("현장 존재 여부", 21), topics.get(11));
    }

    @Test
    void timeoutFallsBackWithoutWaitingAgain() {
        when(client.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));
        var topics = new OpenAiTopicAi(client).generate(SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11));
        assertEquals(new TrialTopic("검사 카드 1", null), topics.get(11));
        verify(client, times(1)).createJson(anyString(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void configurationMissingIsReported() {
        when(client.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CONFIGURATION_MISSING));
        BusinessException error = assertThrows(BusinessException.class, () -> new OpenAiTopicAi(client).generate(
                SpeakingAiFixture.session(Side.PROSECUTION).getSnapshot(), List.of(11)));
        assertEquals(ErrorCode.AI_CONFIGURATION_MISSING, error.getErrorCode());
    }

    private static String entry(int cardId, String topic, String defenseId) {
        return "{\"cardId\":%d,\"topic\":\"%s\",\"defenseCardId\":%s}".formatted(cardId, topic, defenseId);
    }

    private static String response(String... entries) {
        return "{\"topics\":[" + String.join(",", entries) + "]}";
    }

    private void given(String json) {
        when(client.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new AiResult<>(JsonMapper.builder().build().readTree(json), null));
    }
}
