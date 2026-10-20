package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.trial.entity.TrialMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityRuleTest {

    @ParameterizedTest(name = "{0} : 게이지 {1} · 변화량 {2} · 판정 {3} · 내 카드 {4} · 경과 {5}")
    @DisplayName("모드별 공개 범위는 TrialMode 주석 · API 명세와 같다")
    @CsvSource({
            "CHICKEN,    true,  true,  true,  true,  false",
            "CHALLENGER, true,  true,  false, false, true",
            "IRON,       false, false, false, false, false"
    })
    void visibility(TrialMode mode, boolean score, boolean delta, boolean judgement, boolean myCards, boolean progress) {
        assertEquals(score, VisibilityRule.showsScore(mode));
        assertEquals(delta, VisibilityRule.showsDelta(mode));
        assertEquals(judgement, VisibilityRule.showsJudgement(mode));
        assertEquals(myCards, VisibilityRule.showsMyCards(mode));
        assertEquals(progress, VisibilityRule.showsProgress(mode));
    }
}