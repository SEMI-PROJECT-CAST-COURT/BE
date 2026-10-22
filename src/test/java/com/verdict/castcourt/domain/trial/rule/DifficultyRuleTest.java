package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyRuleTest {

    private final TrialTurn opening = new TrialTurn(1, Stage.OPENING, Side.PROSECUTION, Speaker.AI, null);
    private final TrialTurn rebuttal = new TrialTurn(4, Stage.CARD, Side.DEFENSE, Speaker.AI, 11);

    @Test
    @DisplayName("비율 밖의 난수면 허점 없이 발언한다")
    void noneOutsidePercent() {
        assertEquals(DifficultyRule.Weakness.NONE, DifficultyRule.pick(rebuttal, DifficultyRule.WEAKNESS_PERCENT));
        assertEquals(DifficultyRule.Weakness.NONE, DifficultyRule.pick(rebuttal, 99));
    }

    @Test
    @DisplayName("반박 판정이 있는 발언은 근거 누락과 반박 누락을 번갈아 고른다")
    void evidenceOrRebut() {
        assertEquals(DifficultyRule.Weakness.NO_EVIDENCE, DifficultyRule.pick(rebuttal, 0));
        assertEquals(DifficultyRule.Weakness.NO_REBUT, DifficultyRule.pick(rebuttal, 1));
    }

    @Test
    @DisplayName("모두 진술처럼 반박을 판정하지 않는 발언은 근거 누락만 고른다")
    void openingOnlyEvidence() {
        assertEquals(DifficultyRule.Weakness.NO_EVIDENCE, DifficultyRule.pick(opening, 1));
    }
}
