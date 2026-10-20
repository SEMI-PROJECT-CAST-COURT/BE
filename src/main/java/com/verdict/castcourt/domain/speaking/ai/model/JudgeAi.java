package com.verdict.castcourt.domain.speaking.ai.model;

import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;

/* 판사 AI : 발언 하나를 ①~④ 로 판정한다
 *  발언 제출 서비스는 이 인터페이스만 알고, 구현(OpenAiJudgeAi)은 모른다.
 * */
public interface JudgeAi {

    /* session.currentTurn() 차례의 발언 content 를 판정한다 (기록은 부른 쪽이 한다) */
    AiResult<JudgeResult> judge(TrialSession session, String content);
}
