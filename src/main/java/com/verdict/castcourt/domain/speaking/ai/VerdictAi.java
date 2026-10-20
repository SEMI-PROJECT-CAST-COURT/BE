package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;

/* 판사 총평 AI : 끝난 재판의 판결 이유를 설명한다 (판결 받기에서 쓴다)
 *  판결(형벌 단계)은 서버가 게이지로 정하고, AI 는 이유만 쓴다.
 * */
public interface VerdictAi {

    /* 총평 문장 (trial.judge_summary · 1000자 이하) */
    AiResult<String> summarize(TrialSession session);
}