package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.speaking.ai.prompt.SpeakingAiPrompt;
import com.verdict.castcourt.domain.trial.rule.DifficultyRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/* 상대 AI : 사용자의 반대편(검사 또는 변호인)으로 발언한다
 *  OpenAI 스트리밍으로 대사를 받아 글자 조각마다 넘긴다. 대사는 흘려보내야 해서 JSON 이 아닌 글로 받는다.
 * */
@Component
public class OpponentAi {

    // 한국어 100자 안팎 + 여유 (넘치면 문장 중간에 끊기므로 넉넉히 둔다)
    private static final int MAX_OUTPUT_TOKENS = 300;

    private final OpenAiClient openAiClient;

    public OpponentAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /* session.currentTurn() 차례(AI 차례)의 발언을 만든다
     *  글자 조각이 생길 때마다 onDelta 를 부른다 → 발언 제출에서 SSE opponent_delta 이벤트로 보낸다.
     *  다 만들면 전체 문장을 돌려준다 → 판정 · 기록에 쓴다.
     * */
    public AiResult<String> speak(TrialSession session, Consumer<String> onDelta) {

        // 난이도 : 일정 비율로 허점 하나를 남기게 한다 (판사 기준은 그대로)
        DifficultyRule.Weakness weakness = DifficultyRule.pick(session.currentTurn(), ThreadLocalRandom.current().nextInt(100));

        AiResult<String> response = openAiClient.streamText(
                SpeakingAiPrompt.opponentInstructions(session.currentTurn().side()),
                SpeakingAiPrompt.opponentInput(session, weakness),
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
