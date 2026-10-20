package com.verdict.castcourt.domain.trial.dto.request;

/* 내 재판 목록 승패 필터 (DB 에 저장하지 않음 · 역할 + 형벌 단계로 계산) */
public enum TrialOutcome {

    WIN,            // 승리
    LOSE            // 패배
}
