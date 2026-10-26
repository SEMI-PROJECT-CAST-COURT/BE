package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.trial.entity.TrialMode;

/* 모드별 공개 범위 (TrialMode 주석 · API 명세 그대로)
 *  재판 시작 · 재판 상태 응답과 발언 제출 SSE가 같은 규칙을 쓴다
 *
 *  | 공개 항목          | CHICKEN | CHALLENGER | IRON |
 *  | 게이지 · 변화량     |    O    |     O      |  X   |
 *  | 판정 4항목 · 등급   |    O    |     X      |  X   |
 *  | 현재 공방 카드     |    O    |     X      |  X   |
 *  | 사건 경과          |    X    |     O      |  X   |
 * */
public final class VisibilityRule {

    private VisibilityRule() {
    }

    /* 게이지 (score, scoreAfter) */
    public static boolean showsScore(TrialMode mode) {
        return mode != TrialMode.IRON;
    }

    /* 발언마다 게이지 변화량 (delta) */
    public static boolean showsDelta(TrialMode mode) {
        return mode != TrialMode.IRON;
    }

    /* 판사 판정 4항목과 등급 */
    public static boolean showsJudgement(TrialMode mode) {
        return mode == TrialMode.CHICKEN;
    }

    /* 현재 공방 카드 원문 (stage · 상태 응답 card) */
    public static boolean showsCurrentCard(TrialMode mode) {
        return mode == TrialMode.CHICKEN;
    }

    /* 사건 경과 (재판 시작 응답 progress) */
    public static boolean showsProgress(TrialMode mode) {
        return mode == TrialMode.CHALLENGER;
    }
}