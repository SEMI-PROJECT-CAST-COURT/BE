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

/* 판사 AI : 네 항목과 가중치를 받는다. */
@Component
public class JudgeAi {

    // 판정 JSON 은 짧아서 200 토큰이면 충분하다 (비용 상한)
    private static final int MAX_OUTPUT_TOKENS = 200;

    private final OpenAiClient openAiClient;

    public JudgeAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public AiResult<JudgeResult> judge(TrialSession session, String content) {

        AiResult<JsonNode> response = openAiClient.createJson(
                SpeakingAiPrompt.JUDGE_INSTRUCTIONS,
                SpeakingAiPrompt.judgeInput(session, content),
                MAX_OUTPUT_TOKENS,
                "judge_result",
                SpeakingAiPrompt.JUDGE_SCHEMA
        );

        JsonNode json = response.output();
        // 누락·잘못된 타입을 false로 읽어 엉뚱한 등급이 되지 않게 막는다.
        for (String field : new String[]{"issue", "evidence", "rebut", "novel"}) {
            if (!json.path(field).isBoolean()) {
                throw new BusinessException(ErrorCode.AI_JUDGE_FAILED);
            }
        }

        if (!json.path("weight").isInt()) {
            throw new BusinessException(ErrorCode.AI_JUDGE_FAILED);
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
