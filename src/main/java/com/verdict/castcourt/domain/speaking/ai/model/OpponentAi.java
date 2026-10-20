package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;

import java.util.function.Consumer;

/* 상대 AI : 사용자의 반대편(검사 또는 변호인)으로 발언한다 */
public interface OpponentAi {

    /* session.currentTurn() 차례(AI 차례)의 발언을 만든다
     *  글자 조각이 생길 때마다 onDelta 를 부른다 → 발언 제출에서 SSE opponent_delta 이벤트로 보낸다.
     *  다 만들면 전체 문장을 돌려준다 → 판정 · 기록에 쓴다.
     * */
    AiResult<String> speak(TrialSession session, Consumer<String> onDelta);
}
