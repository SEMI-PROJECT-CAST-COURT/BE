package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
        givenJson("{\"issue\":true,\"evidence\":false,\"rebut\":false,\"novel\":true,\"roleViolation\":false,\"weight\":12}");

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
        assertTrue(input.contains("[이번 공방의 내 쪽 근거 카드]\n변호인 카드 : 내용 3"));
        String instructions = com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt.opponentInstructions(Side.DEFENSE);
        assertTrue(instructions.contains("변호인 역할을 맡는다"));
        assertTrue(instructions.contains("아니다"));
    }

    @Test
    @DisplayName("판사는 역할·현재 논점을 받고 역할 이탈을 독립 반환하며, 공방은 카드 가중치를 사용한다")
    void judgeRoleViolation() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        SpeakingAiFixture.moveTo(session, 4);
        givenJson("""
                {"issue":true,"evidence":true,"rebut":true,"novel":true,"roleViolation":true,"weight":20}
                """);

        JudgeResult result = new JudgeAi(openAiClient).judge(session, "검사의 엄벌 요청을 지지합니다.").output();

        assertTrue(result.roleViolation());
        assertTrue(result.issue());
        assertEquals(8, result.weight());
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).createJson(anyString(), input.capture(), anyInt(), eq("judge_result"), any());
        assertTrue(input.getValue().contains("발언자 : 변호인"));
        assertTrue(input.getValue().contains("상대 : 검사"));
        assertTrue(input.getValue().contains("[현재 논점]"));
        assertTrue(input.getValue().contains("현장 존재 여부"));
        org.junit.jupiter.api.Assertions.assertFalse(input.getValue().contains("행동의 의도"));
        assertTrue(((java.util.List<?>) SpeakingAiPrompt.JUDGE_SCHEMA.get("required")).contains("roleViolation"));
    }

    @Test
    @DisplayName("대응 카드가 없는 변호인 공방은 다른 카드를 대신 선정하지 않는다")
    void defenseWithoutCounterCard() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        SpeakingAiFixture.moveTo(session, 10);
        String input = SpeakingAiPrompt.opponentInput(session);
        assertTrue(input.contains("[현재 논점]\n행동의 의도"));
        assertTrue(input.contains("[지금 카드] 검사 카드 2"));
        assertTrue(input.contains("[이번 공방의 내 쪽 근거 카드]\n없음"));
    }

    @Test
    @DisplayName("상대 AI 는 같은 공방의 자기 이전 발언과 공방 번호별 발언 방향을 받는다")
    void opponentAvoidsRepeatingInSameCard() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);   // AI 가 변호인
        SpeakingAiFixture.moveTo(session, 4);
        String first = SpeakingAiPrompt.opponentInput(session);
        assertTrue(first.contains("[이번 공방의 내 이전 발언]\n없음"));
        assertTrue(first.contains("내 쪽의 핵심 주장 하나를 세운다"));

        SpeakingAiFixture.moveTo(session, 6);
        String second = SpeakingAiPrompt.opponentInput(session);
        assertTrue(second.contains("[이번 공방의 내 이전 발언]\n- "));
        assertTrue(second.contains("근거를 요구하는 반문"));
        org.junit.jupiter.api.Assertions.assertFalse(SpeakingAiPrompt.opponentInput(
                SpeakingAiFixture.session(Side.DEFENSE)).contains("[이번 공방의 내 이전 발언]"));
    }

    @Test
    @DisplayName("상대 AI 허점은 [이번 발언 제약]으로만 전달하고 허점이 없으면 붙이지 않는다")
    void opponentWeakness() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        SpeakingAiFixture.moveTo(session, 4);
        String weak = SpeakingAiPrompt.opponentInput(session,
                com.verdict.castcourt.domain.trial.rule.DifficultyRule.Weakness.NO_REBUT);
        assertTrue(weak.contains("[이번 발언 제약]\n직전 상대 발언의 핵심 주장에 직접 답하지 말고"));
        org.junit.jupiter.api.Assertions.assertFalse(SpeakingAiPrompt.opponentInput(session).contains("[이번 발언 제약]"));
    }

    @ParameterizedTest
    @MethodSource("invalidJudgeResponses")
    @DisplayName("역할 이탈 누락·null·잘못된 타입을 정상 판정으로 수용하지 않는다")
    void judgeInvalidTypes(String response) {
        givenJson(response);
        BusinessException e = assertThrows(BusinessException.class,
                () -> new JudgeAi(openAiClient).judge(SpeakingAiFixture.session(Side.DEFENSE), "발언"));
        assertEquals(ErrorCode.AI_JUDGE_FAILED, e.getErrorCode());
    }

    static java.util.stream.Stream<String> invalidJudgeResponses() {
        return java.util.stream.Stream.of(
                """
                {"issue":true,"evidence":true,"rebut":true,"novel":true,"weight":10}
                """,
                """
                {"issue":true,"evidence":true,"rebut":true,"novel":true,"roleViolation":null,"weight":10}
                """,
                """
                {"issue":true,"evidence":true,"rebut":true,"novel":true,"roleViolation":"false","weight":10}
                """,
                """
                {"issue":"true","evidence":true,"rebut":true,"novel":true,"roleViolation":false,"weight":10}
                """,
                """
                {"issue":true,"evidence":true,"rebut":true,"novel":true,"roleViolation":false,"weight":"10"}
                """);
    }

    @Test
    @DisplayName("총평에는 서버가 확정한 역할 이탈·실제 벌점을 전달한다")
    void verdictUsesServerAssessment() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        session.record("무죄를 요청합니다.", true, true, null, true, true, 10);
        String input = SpeakingAiPrompt.verdictInput(session);
        assertTrue(input.contains("[서버 평가 결과]"));
        assertTrue(input.contains("역할 이탈 true · 등급 STRONG · 실제 게이지 변화 -3"));
        assertTrue(input.contains("최종 게이지 47"));
    }

    private void givenJson(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        when(openAiClient.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new AiResult<>(node, USAGE));
    }
}
