package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyRuleTest {

    private final TrialTurn opening = new TrialTurn(1, Stage.OPENING, Side.PROSECUTION, Speaker.AI, null);
    private final TrialTurn rebuttal = new TrialTurn(4, Stage.CARD, Side.DEFENSE, Speaker.AI, 11);

    // nextInt(100) 첫 값을 roll 로 고정한다 (섞기는 기본 Random 동작)
    private static Random rolling(int roll) {
        return new Random(1) {
            private boolean first = true;

            @Override
            public int nextInt(int bound) {
                if (first) {
                    first = false;
                    return roll;
                }
                return super.nextInt(bound);
            }
        };
    }

    @Test
    @DisplayName("허점 개수 비율의 합은 100이다")
    void percentSum() {
        assertEquals(100, Arrays.stream(DifficultyRule.COUNT_PERCENT).sum());
    }

    @Test
    @DisplayName("난수 구간마다 허점 0 · 1 · 2 · 3개를 고른다")
    void countByRoll() {
        assertTrue(DifficultyRule.pick(rebuttal, rolling(0)).isEmpty());
        assertTrue(DifficultyRule.pick(rebuttal, rolling(4)).isEmpty());
        assertEquals(1, DifficultyRule.pick(rebuttal, rolling(5)).size());
        assertEquals(1, DifficultyRule.pick(rebuttal, rolling(24)).size());
        assertEquals(2, DifficultyRule.pick(rebuttal, rolling(25)).size());
        assertEquals(2, DifficultyRule.pick(rebuttal, rolling(74)).size());
        assertEquals(3, DifficultyRule.pick(rebuttal, rolling(75)).size());
        assertEquals(3, DifficultyRule.pick(rebuttal, rolling(99)).size());
    }

    @Test
    @DisplayName("반박을 판정하지 않는 발언은 반박 누락을 고르지 않아 최대 2개다")
    void openingWithoutRebut() {
        Set<DifficultyRule.Weakness> picked = DifficultyRule.pick(opening, rolling(99));
        assertEquals(2, picked.size());
        assertFalse(picked.contains(DifficultyRule.Weakness.NO_REBUT));
    }
}
