package com.verdict.castcourt.domain.speaking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSessionSpeaking;

/* SSE judge · opponent_judge 이벤트 : 발언 하나의 판정
 *  모드별 공개 범위 (명세 v6)
 *  - CHICKEN    : checks · grade · delta · scoreAfter 전부
 *  - CHALLENGER : delta · scoreAfter 만
 *  - IRON       : 없음 → data 가 {} 로 간다 (이벤트 자체는 보내서 화면이 다음 순서로 넘어가게 한다)
 * */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakingJudgeEventDTO(
        Checks checks,
        Grade grade,
        Boolean roleViolation,
        Integer delta,
        Integer scoreAfter
) {

    /* ①~④ : 안쪽 record 라 바깥의 NON_NULL 이 적용되지 않는다 → rebut 이 null 이면 "rebut":null 로 보인다 (명세 예시와 같음) */
    public record Checks(boolean issue, boolean evidence, Boolean rebut, boolean novel) {
    }

    public static SpeakingJudgeEventDTO of(TrialSessionSpeaking speaking, TrialMode mode) {

        boolean judgement = VisibilityRule.showsJudgement(mode);
        return new SpeakingJudgeEventDTO(
                judgement ? new Checks(speaking.issue(), speaking.evidence(), speaking.rebut(), speaking.novel()) : null,
                judgement ? speaking.grade() : null,
                judgement ? speaking.roleViolation() : null,
                VisibilityRule.showsDelta(mode) ? speaking.delta() : null,
                VisibilityRule.showsScore(mode) ? speaking.scoreAfter() : null
        );
    }
}