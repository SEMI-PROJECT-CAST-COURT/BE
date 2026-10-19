package com.verdict.castcourt.domain.penaltytier.repository;

import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyTierRepository extends JpaRepository<PenaltyTierEntity, Integer> {
    List<PenaltyTierEntity> findByWorkCaseId(int workCaseId);
}
