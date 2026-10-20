package com.verdict.castcourt.domain.trial.entity;

/* 측 : 사용자가 맡은 역할(trial.role), 발언한 쪽(speaking.side), 근거 카드의 측(evidence_card.side) */
public enum Side {

    PROSECUTION,    // 검사 : 게이지를 100(유죄) 쪽으로
    DEFENSE;        // 변호인 : 게이지를 0(무죄) 쪽으로

    /* 반대편 (사용자가 검사면 AI 는 변호인) */
    public Side opposite() {
        return this == PROSECUTION ? DEFENSE : PROSECUTION;
    }
}