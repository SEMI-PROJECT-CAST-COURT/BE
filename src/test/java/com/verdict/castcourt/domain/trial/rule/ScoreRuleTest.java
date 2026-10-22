package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreRuleTest {

    @Test
    @DisplayName("변화량은 가중치 x 배율이고 검사는 +, 변호인은 -")
    void deltaSign() {
        assertEquals(8, ScoreRule.delta(8, Grade.STRONG, Side.PROSECUTION, false));
        assertEquals(-8, ScoreRule.delta(8, Grade.STRONG, Side.DEFENSE, false));
        assertEquals(0, ScoreRule.delta(8, Grade.OFF, Side.DEFENSE, false));
    }

    @Test
    @DisplayName("변화량의 소수점은 반올림한다")
    void deltaRounding() {
        assertEquals(2, ScoreRule.delta(8, Grade.WEAK, Side.PROSECUTION, false));   // 2.4 -> 2
        assertEquals(2, ScoreRule.delta(5, Grade.WEAK, Side.PROSECUTION, false));   // 1.5 -> 2
        assertEquals(3, ScoreRule.delta(5, Grade.OK, Side.PROSECUTION, false));     // 3.0 -> 3
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,1", "5,2", "8,2", "10,3", "20,6"})
    @DisplayName("역할 이탈은 등급과 무관하게 30% 반올림·최소 1점, 자기 진영에 불리한 벌점만 적용")
    void roleViolationPenalty(int weight, int penalty) {
        for (Grade grade : Grade.values()) {
            assertEquals(-penalty, ScoreRule.delta(weight, grade, Side.PROSECUTION, true));
            assertEquals(penalty, ScoreRule.delta(weight, grade, Side.DEFENSE, true));
        }
        assertEquals(0, ScoreRule.applyDelta(0, ScoreRule.delta(weight, Grade.STRONG, Side.PROSECUTION, true)));
        assertEquals(100, ScoreRule.applyDelta(100, ScoreRule.delta(weight, Grade.STRONG, Side.DEFENSE, true)));
    }

    @Test
    @DisplayName("게이지는 0~100 밖으로 나가지 않는다")
    void applyDeltaClamp() {
        assertEquals(100, ScoreRule.applyDelta(95, 10));
        assertEquals(0, ScoreRule.applyDelta(3, -8));
        assertEquals(58, ScoreRule.applyDelta(50, 8));
    }

    @ParameterizedTest(name = "게이지 {0} -> 형벌 단계 {1}")
    @DisplayName("게이지 구간마다 형벌 단계가 정해진다 (50은 무죄 1단계)")
    @CsvSource({
            "0, 1", "16, 1",
            "17, 2", "33, 2",
            "34, 3", "50, 3",
            "51, 4", "66, 4",
            "67, 5", "83, 5",
            "84, 6", "100, 6"
    })
    void penaltyTier(int score, int expectedTier) {
        assertEquals(expectedTier, ScoreRule.penaltyTier(score));
    }

    @Test
    @DisplayName("형벌 단계 이름은 1~3 무죄 3·2·1단계, 4~6 유죄 1·2·3단계")
    void tierTitle() {
        assertEquals("무죄 3단계", ScoreRule.tierTitle(1));
        assertEquals("무죄 1단계", ScoreRule.tierTitle(3));
        assertEquals("유죄 1단계", ScoreRule.tierTitle(4));
        assertEquals("유죄 3단계", ScoreRule.tierTitle(6));
    }

    @Test
    @DisplayName("변호인은 무죄면, 검사는 유죄면 승리")
    void isWin() {
        assertTrue(ScoreRule.isWin(Side.DEFENSE, 3));
        assertFalse(ScoreRule.isWin(Side.DEFENSE, 4));
        assertTrue(ScoreRule.isWin(Side.PROSECUTION, 4));
        assertFalse(ScoreRule.isWin(Side.PROSECUTION, 3));
    }
}