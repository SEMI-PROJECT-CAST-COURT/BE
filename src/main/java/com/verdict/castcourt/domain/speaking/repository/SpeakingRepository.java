package com.verdict.castcourt.domain.speaking.repository;

import com.verdict.castcourt.domain.speaking.entity.Speaking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingRepository extends JpaRepository<Speaking, Integer> {

    // 재판 하나의 발언을 순서(seq)대로 (trialId 는 speaking.trial.id 로 해석된다)
    List<Speaking> findByTrialIdOrderBySeqAsc(Integer trialId);
}