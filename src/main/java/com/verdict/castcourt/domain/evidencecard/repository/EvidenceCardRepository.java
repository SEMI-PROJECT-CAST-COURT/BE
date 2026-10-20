package com.verdict.castcourt.domain.evidencecard.repository;

import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceCardRepository extends JpaRepository<EvidenceCardEntity, Integer> {
    List<EvidenceCardEntity> findByWorkCaseId(int workCaseId);
    List<EvidenceCardEntity> findByIdInAndWorkCaseId(List<Integer> ids, int workCaseId);
}
