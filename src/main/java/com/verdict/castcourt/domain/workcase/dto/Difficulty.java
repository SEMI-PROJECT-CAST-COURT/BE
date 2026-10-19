package com.verdict.castcourt.domain.workcase.dto;

/* 난이도 */
public enum Difficulty {
    EASY,
    NORMAL,
    HARD;

    private static final int THRESHOLD = 6;

    // (내 측 합) - (상대 측 합)
    public static Difficulty of(int mySum, int opponentSum) {

        int diff = mySum - opponentSum;

        if(diff >= THRESHOLD) return EASY;
        else if(diff <= -THRESHOLD) return HARD;
        return NORMAL;
    }
}
