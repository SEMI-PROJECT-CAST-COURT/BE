package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.Trial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrialRepository extends JpaRepository<Trial, Integer> {
}