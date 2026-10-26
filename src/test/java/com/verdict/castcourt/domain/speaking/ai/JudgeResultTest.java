package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.verdict.castcourt.domain.trial.rule.DifficultyRule;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeResultTest {

    @ParameterizedTest(name = "seq {0} : 반박 {1} · 가중치 {2}")
    @DisplayName("③ 반박은 모두 진술 · 카드 첫 공격에서 빠지고, 공방 가중치는 카드 가중치를 쓴다")
    @CsvSource({
            "1,  ,     20",     // 모두 진술 검사 : ③ 제외 · AI 가중치 25 → 20 으로 자름
            "2,  ,     20",     // 모두 진술 변호인
            "3,  ,     8",      // 카드 11 첫 공격 : ③ 제외 · 카드 가중치 8
            "4,  false, 8",     // 카드 11 변호인 반론
            "5,  false, 8",     // 카드 11 두 번째 공격 : 직전 변호인 발언이 있으니 ③ 판정
            "9,  ,     12",     // 카드 12 첫 공격
            "15, false, 20"     // 최후 변론 검사 : 직전 변호인 발언이 있으니 ③ 판정
    })
    void rebutAndWeight(int seq, Boolean rebut, int weight) {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);
        SpeakingAiFixture.moveTo(session, seq);

        JudgeResult result = JudgeResult.of(session, true, true, false, true, 25);

        assertEquals(rebut, result.rebut());
        assertEquals(weight, result.weight());
    }

    @Test
    @DisplayName("남기게 한 허점은 판사가 충족으로 봤어도 미충족으로 확정하고 ① · 가중치는 그대로 둔다")
    void weaknessesForceMiss() {
        JudgeResult all = new JudgeResult(true, true, true, true, 8);

        JudgeResult weak = all.withWeaknesses(EnumSet.allOf(DifficultyRule.Weakness.class));
        assertTrue(weak.issue());
        assertFalse(weak.evidence());
        assertFalse(weak.rebut());
        assertFalse(weak.novel());
        assertEquals(8, weak.weight());

        assertEquals(all, all.withWeaknesses(Set.of()));
        assertNull(new JudgeResult(true, true, null, true, 8)
                .withWeaknesses(Set.of(DifficultyRule.Weakness.NO_REBUT)).rebut());
    }
}