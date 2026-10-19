package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.speaking.repository.SpeakingRepository;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/* 더미 데이터(INSERT_DATA.sql)가 들어 있는 실제 DB 로 엔티티 매핑을 확인한다 (읽기만 함) */
@SpringBootTest
@Transactional
class TrialRepositoryTest {

    @Autowired
    private TrialRepository trialRepository;

    @Autowired
    private SpeakingRepository speakingRepository;

    @Test
    @DisplayName("더미 재판 1번을 읽으면 JSON 컬럼까지 객체로 바뀐다")
    void findDummyTrial() {
        TrialEntity trial = trialRepository.findById(1).orElseThrow();

        assertEquals(4, trial.getUserId());
        assertEquals(7, trial.getWorkCaseId());
        assertEquals(Side.DEFENSE, trial.getRole());
        assertEquals(TrialMode.CHICKEN, trial.getMode());
        assertEquals(List.of(40, 42), trial.getCardIds());
        assertEquals(40, trial.getScore());
        assertEquals(3, trial.getPenaltyTier());

        // case_snapshot JSON 이 CaseSnapshot 객체로 읽혔는지
        assertEquals(7, trial.getCaseSnapshot().caseId());
        assertFalse(trial.getCaseSnapshot().cards().isEmpty());
        assertEquals(6, trial.getCaseSnapshot().penalties().size());
    }

    @Test
    @DisplayName("더미 재판 1번의 발언을 순서대로 읽는다")
    void findDummySpeakings() {
        List<SpeakingEntity> speakings = speakingRepository.findByTrialIdOrderBySeqAsc(1);

        assertFalse(speakings.isEmpty());

        SpeakingEntity first = speakings.get(0);
        assertEquals(1, first.getSeq());
        assertEquals(Stage.OPENING, first.getStage());
        assertEquals(Side.PROSECUTION, first.getSide());
        assertEquals(Speaker.AI, first.getSpeaker());
        assertNull(first.getRebut());
        assertEquals(Grade.STRONG, first.getGrade());
        assertEquals(58, first.getScoreAfter());
    }
}