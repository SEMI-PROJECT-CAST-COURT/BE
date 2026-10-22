package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.trial.session.TrialTurn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/* 상대 AI 난이도 : 판사 기준은 그대로 두고, 상대 발언에 일부러 허점을 남겨 유저가 반격할 틈을 만든다
 *  허점 1개 → 보통 OK(60%), 2개 이상 → WEAK(30%)
 * */
public final class DifficultyRule {

    // 허점 개수 비율 (%) : 0개 · 1개 · 2개 · 3개 순서, 합 100
    static final int[] COUNT_PERCENT = {25, 40, 25, 10};

    public enum Weakness {
        NO_EVIDENCE,    // ② 근거 : 사건 · 카드 사실 인용 없이 주장만
        NO_REBUT,       // ③ 반박 : 직전 상대 주장에 답하지 않음
        REPEAT          // ④ 새로움 : 앞의 주장을 표현만 바꿔 되풀이
    }

    private DifficultyRule() {
    }

    /* 이번 발언에 남길 허점들
     *  반박을 판정하지 않는 발언(모두 진술 · 카드 첫 공격)은 반박 누락을 고르지 않는다.
     * */
    public static Set<Weakness> pick(TrialTurn turn, Random random) {

        int roll = random.nextInt(100);
        int count = 0;
        int cumulative = COUNT_PERCENT[0];
        while (roll >= cumulative) {
            count++;
            cumulative += COUNT_PERCENT[count];
        }

        List<Weakness> candidates = new ArrayList<>(List.of(Weakness.NO_EVIDENCE, Weakness.REPEAT));
        if (JudgeRule.needsRebut(turn)) {
            candidates.add(Weakness.NO_REBUT);
        }
        Collections.shuffle(candidates, random);

        Set<Weakness> picked = EnumSet.noneOf(Weakness.class);
        picked.addAll(candidates.subList(0, Math.min(count, candidates.size())));
        return picked;
    }
}
