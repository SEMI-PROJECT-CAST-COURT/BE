package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/* 상대 AI : OpenAI 스트리밍으로 대사를 받아 글자 조각마다 넘긴다
 *  대사는 흘려보내야 해서 JSON 이 아닌 글로 받는다.
 * */
@Component
public class OpenAiOpponentAi implements OpponentAi {

    // 한국어 300자 안팎 + 여유
    private static final int MAX_OUTPUT_TOKENS = 600;

    private final OpenAiClient openAiClient;

    public OpenAiOpponentAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @Override
    public AiResult<String> speak(TrialSession session, Consumer<String> onDelta) {

        AiResult<String> response = openAiClient.streamText(
                SpeakingAiPrompt.opponentInstructions(session.currentTurn().side()),
                SpeakingAiPrompt.opponentInput(session),
                MAX_OUTPUT_TOKENS,
                onDelta
        );

        // 빈 대사는 기록할 수 없다 → 실패 (B-1 문구 "제대로 답변을 생성하지 못했습니다.")
        String text = response.output().strip();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_OPPONENT_FAILED);
        }
        return new AiResult<>(text, response.usage());
    }
}
