package com.verdict.castcourt.domain.speaking.entity;

/* 재판 단계 (speaking.stage) */
public enum Stage {

    OPENING,        // 모두 진술 : 검사 → 변호인 1번씩
    CARD,           // 공방 : 공방 카드마다 검사 공격 · 변호인 반론 3번씩
    CLOSING         // 최후 변론 : 검사 → 변호인
}