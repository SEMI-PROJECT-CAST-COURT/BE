package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/* 실제 판사 AI : OpenAI 구조화 출력으로 ①~④ 와 가중치를 받는다
 *  castcourt.ai.mode = openai 일 때만 빈으로 뜬다.
 * */
@Component
@ConditionalOnProperty(name = "castcourt.ai.mode", havingValue = "openai")
public class OpenAiJudgeAi implements JudgeAi {

    // 판정 JSON 은 짧아서 200 토큰이면 충분하다 (비용 상한)
    private static final int MAX_OUTPUT_TOKENS = 200;

    private final OpenAiClient openAiClient;

    public OpenAiJudgeAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @Override
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