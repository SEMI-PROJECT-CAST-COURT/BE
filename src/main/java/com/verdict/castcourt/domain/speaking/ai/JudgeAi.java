package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;

/* 판사 AI : 발언 하나를 ①~④ 로 판정한다
 *  구현은 둘 (가짜 FakeJudgeAi / 실제 OpenAiJudgeAi). 설정값 하나로 둘 중 하나만 빈으로 뜬다. (5-5)
 *  6장의 발언 제출 서비스는 이 인터페이스만 알고, 어느 구현이 들어왔는지 모른다.
 * */
public interface JudgeAi {

    /* session.currentTurn() 차례의 발언 content 를 판정한다 (기록은 부른 쪽이 한다) */
    AiResult<JudgeResult> judge(TrialSession session, String content);
}