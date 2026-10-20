package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/* 가짜 판사 총평 AI : 게이지와 형벌 단계만 넣은 정해진 문장 */
@Component
@ConditionalOnProperty(name = "castcourt.ai.mode", havingValue = "fake", matchIfMissing = true)
public class FakeVerdictAi implements VerdictAi {

    @Override
    public AiResult<String> summarize(TrialSession session) {

        int tier = ScoreRule.penaltyTier(session.getScore());
        String summary = "본 법정은 양측의 주장을 모두 검토하였습니다. 최종 게이지 " + session.getScore()
                + "에 따라 " + ScoreRule.tierTitle(tier) + "을 선고합니다.";

        return new AiResult<>(summary, AiUsage.fake(LocalDateTime.now(), 0));
    }
}