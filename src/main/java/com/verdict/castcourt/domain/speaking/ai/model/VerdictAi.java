package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/* 판사 총평 AI : 끝난 재판의 판결 이유를 설명한다 (판결 받기에서 쓴다)
 *  판결(형벌 단계)은 서버가 게이지로 정하고, AI 는 이유만 OpenAI 로 쓴다.
 * */
@Component
public class VerdictAi {

    // 500자 총평 + 여유
    private static final int MAX_OUTPUT_TOKENS = 1000;
    // trial.judge_summary 컬럼 길이
    private static final int SUMMARY_COLUMN_LENGTH = 1000;

    private final OpenAiClient openAiClient;

    public VerdictAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /* 총평 문장 (trial.judge_summary · 1000자 이하) */
    public AiResult<String> summarize(TrialSession session) {

        AiResult<JsonNode> response = openAiClient.createJson(
                SpeakingAiPrompt.VERDICT_INSTRUCTIONS,
                SpeakingAiPrompt.verdictInput(session),
                MAX_OUTPUT_TOKENS,
                "verdict_summary",
                SpeakingAiPrompt.SUMMARY_SCHEMA
        );

        String summary = response.output().path("summary").asString("").strip();
        // 총평이 비면 명세의 판결 받기 502 코드로 알린다
        if (summary.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }
        // AI 가 길이 지시를 넘겨도 DB 저장이 실패하지 않게 컬럼 길이에서 자른다
        if (summary.length() > SUMMARY_COLUMN_LENGTH) {
            summary = summary.substring(0, SUMMARY_COLUMN_LENGTH);
        }
        return new AiResult<>(summary, response.usage());
    }
}
