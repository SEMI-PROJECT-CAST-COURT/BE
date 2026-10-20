package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.trial.entity.Side;

/* 게이지 · 형벌 단계 계산 규칙 (기획서 '게이지와 판결', '판정' 표 그대로) */
public final class ScoreRule {

    public static final int START_SCORE = 50;   // 재판 시작 게이지
    public static final int MIN_SCORE = 0;      // 0 = 무죄 쪽 끝
    public static final int MAX_SCORE = 100;    // 100 = 유죄 쪽 끝

    private ScoreRule() {
    }

    /* 변화량 = 가중치 × 등급 배율 (소수점은 반올림) · 검사 발언은 +, 변호인 발언은 − */
    public static int delta(int weight, Grade grade, Side side) {

        int amount = (int) Math.round(weight * grade.getPercent() / 100.0);

        return side == Side.PROSECUTION ? amount : -amount;
    }

    /* 발언 뒤 게이지 : 0~100 밖으로 나가지 않게 자른다 */
    public static int applyDelta(int score, int delta) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score + delta));
    }

    /* 최종 게이지 → 형벌 단계 1~6 (정확히 50 이면 무죄 1단계 = 3) */
    public static int penaltyTier(int score) {
        if (score <= 16) {
            return 1;
        }
        if (score <= 33) {
            return 2;
        }
        if (score <= 50) {
            return 3;
        }
        if (score <= 66) {
            return 4;
        }
        if (score <= 83) {
            return 5;
        }
        return 6;
    }

    /* 형벌 단계 이름 : 1~3 → 무죄 3 · 2 · 1단계, 4~6 → 유죄 1 · 2 · 3단계 */
    public static String tierTitle(int tier) {
        return tier <= 3 ? "무죄 " + (4 - tier) + "단계" : "유죄 " + (tier - 3) + "단계";
    }

    /* 승패 : 변호인은 무죄(1~3), 검사는 유죄(4~6)면 승리 */
    public static boolean isWin(Side role, int tier) {
        return role == Side.DEFENSE ? tier <= 3 : tier >= 4;
    }
}