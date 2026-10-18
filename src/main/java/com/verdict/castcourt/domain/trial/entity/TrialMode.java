package com.verdict.castcourt.domain.trial.entity;

/* 재판 모드 : 화면에 공개하는 범위만 다르다 (trial.mode) */
public enum TrialMode {

    CHICKEN,        // 겁쟁이 : 게이지 · 발언 판정 전부 · 내 쪽 근거 카드 공개
    CHALLENGER,     // 도전자 : 게이지 · 변화량 · 사건 경과 공개
    IRON            // 철인 : 판결 전까지 게이지 · 판정 비공개
}