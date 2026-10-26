package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.Side;

/* 내 통계 쿼리(TrialRepository.countByRoleAndTier)의 한 줄 : (역할, 형벌 단계)별 판 수 */
public interface TrialStatRow {

    Side getRole();

    Integer getPenaltyTier();

    Long getTrialCount();
}
