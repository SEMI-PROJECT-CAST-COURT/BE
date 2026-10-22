package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.model.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.speaking.ai.model.VerdictAi;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 실제 AI 구현 : OpenAiClient 는 가짜(@Mock)로 두고, 프롬프트 입력과 응답 해석만 확인한다 */
@ExtendWith(MockitoExtension.class)
class OpenAiSpeakingAiTest {

    private static final AiUsage USAGE = new AiUsage("test-model", 100, 10, LocalDateTime.now(), 5);

    @Mock
    private OpenAiClient openAiClient;

    @Test
    @DisplayName("판사 판정 : 모두 진술이면 직전 상대 발언 '없음' 을 보내고, AI 의 ③ 은 버린다")
    void judgeOpening() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        givenJson("{\"issue\":true,\"evidence\":false,\"rebut\":false,\"novel\":true,\"weight\":12}");

        JudgeResult result = new JudgeAi(openAiClient).judge(session, "내 발언").output();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).createJson(anyString(), input.capture(), anyInt(), eq("judge_result"), any());
        assertTrue(input.getValue().contains("[직전 상대 발언]\n없음"));
        assertTrue(input.getValue().contains("[이번 발언] (검사)\n내 발언"));

        assertNull(result.rebut());
        assertEquals(false, result.evidence());
        assertEquals(12, result.weight());
    }

    @Test
    @DisplayName("판사 판정 : 필드가 빠진 응답은 AI_JUDGE_FAILED")
    void judgeMissingField() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        givenJson("{\"issue\":true}");

        BusinessException e = assertThrows(BusinessException.class,
                () -> new JudgeAi(openAiClient).judge(session, "내 발언"));
        assertEquals(ErrorCode.AI_JUDGE_FAILED, e.getErrorCode());
    }

    @Test
    @DisplayName("상대 AI : 자기 쪽 카드만 보내고, 빈 대사는 AI_OPPONENT_FAILED")
    void opponent() {
        TrialSession session = SpeakingAiFixture.session(Side.DEFENSE);     // AI 가 검사
        when(openAiClient.streamText(anyString(), anyString(), anyInt(), any()))
                .thenReturn(new AiResult<>("  ", USAGE));

        BusinessException e = assertThrows(BusinessException.class,
                () -> new OpponentAi(openAiClient).speak(session, delta -> { }));
        assertEquals(ErrorCode.AI_OPPONENT_FAILED, e.getErrorCode());

        ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).streamText(instructions.capture(), input.capture(), anyInt(), any());
        assertTrue(instructions.getValue().contains("검사 역할을 맡는다"));
        assertTrue(input.getValue().contains("검사 카드 1"));
        assertTrue(!input.getValue().contains("변호인 카드"));
    }

    @Test
    @DisplayName("판사 총평 : 서버가 정한 판결을 입력에 넣고 summary 를 돌려준다")
    void verdict() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        givenJson("{\"summary\":\"총평입니다.\"}");

        String summary = new VerdictAi(openAiClient).summarize(session).output();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).createJson(anyString(), input.capture(), anyInt(), eq("verdict_summary"), any());
        assertTrue(input.getValue().contains("최종 게이지 50"));
        assertTrue(input.getValue().contains("무죄 1단계 · 무죄 1"));
        assertEquals("총평입니다.", summary);
    }

    @Test
    @DisplayName("AI 변호인은 피의자·피해자·상대 역할과 현재 논점만 전달받는다")
    void defenseIdentityAndTopic() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        SpeakingAiFixture.moveTo(session, 4);
        String input = com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt.opponentInput(session);
        assertTrue(input.contains("내 역할 : 변호인"));
        assertTrue(input.contains("상대 역할 : 검사"));
        assertTrue(input.contains("피의자 : 피의자"));
        assertTrue(input.contains("피해자 : 피해자"));
        assertTrue(input.contains("[현재 논점]\n현장 존재 여부"));
        org.junit.jupiter.api.Assertions.assertFalse(input.contains("행동의 의도"));
        assertTrue(input.contains("[지금 카드] 검사 카드 1"));
        String instructions = com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt.opponentInstructions(Side.DEFENSE);
        assertTrue(instructions.contains("변호인 역할을 맡는다"));
        assertTrue(instructions.contains("아니다"));
    }

    private void givenJson(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        when(openAiClient.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new AiResult<>(node, USAGE));
    }
}
