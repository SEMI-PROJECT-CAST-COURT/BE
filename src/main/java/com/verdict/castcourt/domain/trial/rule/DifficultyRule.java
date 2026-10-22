package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.trial.session.TrialTurn;

/* 상대 AI 난이도 : 판사 기준은 그대로 두고, 상대 발언에 일부러 허점을 남겨 유저가 반격할 틈을 만든다
 *  허점 하나 = 판사 ② 근거 또는 ③ 반박 하나 미충족 → 보통 STRONG 이 OK(60%)가 된다.
 * */
public final class DifficultyRule {

    // 상대 AI 발언 중 허점을 남기는 비율 (%)
    public static final int WEAKNESS_PERCENT = 60;

    public enum Weakness {
        NONE,           // 허점 없음
        NO_EVIDENCE,    // 사건 · 카드 사실 인용 없이 주장만
        NO_REBUT        // 직전 상대 주장에 답하지 않음
    }

    private DifficultyRule() {
    }

    /* roll : 0~99 난수
     *  반박을 판정하지 않는 발언(모두 진술 · 카드 첫 공격)은 반박 누락이 의미가 없어 근거 누락만 고른다.
     * */
    public static Weakness pick(TrialTurn turn, int roll) {
        if (roll >= WEAKNESS_PERCENT) {
            return Weakness.NONE;
        }
        if (!JudgeRule.needsRebut(turn) || roll % 2 == 0) {
            return Weakness.NO_EVIDENCE;
        }
        return Weakness.NO_REBUT;
    }
}
