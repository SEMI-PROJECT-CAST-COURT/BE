package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/* 판사 AI : 발언 하나를 ①~④ 로 판정한다
 *  OpenAI 구조화 출력(JSON 스키마)으로 ①~④ 와 가중치를 받는다.
 * */
@Component
public class JudgeAi {

    // 판정 JSON 은 짧아서 200 토큰이면 충분하다 (비용 상한)
    private static final int MAX_OUTPUT_TOKENS = 200;

    private final OpenAiClient openAiClient;

    public JudgeAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /* session.currentTurn() 차례의 발언 content 를 판정한다 (기록은 부른 쪽이 한다) */
    public AiResult<JudgeResult> judge(TrialSession session, String content) {

        AiResult<JsonNode> response = openAiClient.createJson(
                SpeakingAiPrompt.JUDGE_INSTRUCTIONS,
                SpeakingAiPrompt.judgeInput(session, content),
                MAX_OUTPUT_TOKENS,
                "judge_result",
                SpeakingAiPrompt.JUDGE_SCHEMA
        );

        JsonNode json = response.output();
        // strict 스키마라 보통은 다 오지만, 하나라도 빠지면 판정을 믿을 수 없으니 실패로 본다 (B-1 : 저장 · 게이지 변화 없음)
        for (String field : new String[]{"issue", "evidence", "rebut", "novel", "weight"}) {
            if (!json.has(field)) {
                throw new BusinessException(ErrorCode.AI_JUDGE_FAILED);
            }
        }

        JudgeResult result = JudgeResult.of(
                session,
                json.path("issue").asBoolean(),
                json.path("evidence").asBoolean(),
                json.path("rebut").asBoolean(),
                json.path("novel").asBoolean(),
                json.path("weight").asInt()
        );
        return new AiResult<>(result, response.usage());
    }
}
