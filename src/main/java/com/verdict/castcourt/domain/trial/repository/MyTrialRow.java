package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;

/* 내 재판 목록 쿼리(TrialRepository.findMyTrials)의 한 줄
 *  작품 제목 · 쟁점은 사건 스냅샷에서 꺼낸다 (사건이 수정 · 삭제돼도 재판 당시 값 유지)
 *  caseUsage · workUsage 는 지금 사건 · 작품의 is_usage (삭제 여부 표시용)
 * */
public interface MyTrialRow {

    Integer getTrialId();

    CaseSnapshot getCaseSnapshot();

    Side getRole();

    Integer getPenaltyTier();

    Boolean getCaseUsage();

    Boolean getWorkUsage();
}
