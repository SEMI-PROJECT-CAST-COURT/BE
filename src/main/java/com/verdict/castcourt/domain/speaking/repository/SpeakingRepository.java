package com.verdict.castcourt.domain.speaking.repository;

import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingRepository extends JpaRepository<SpeakingEntity, Integer> {

    // 재판 하나의 발언을 순서(seq)대로 (trialId 는 speaking.trial.id 로 해석된다)
    List<SpeakingEntity> findByTrialIdOrderBySeqAsc(Integer trialId);
}