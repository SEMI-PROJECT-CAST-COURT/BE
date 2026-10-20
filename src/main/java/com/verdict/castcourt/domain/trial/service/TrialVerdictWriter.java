package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import com.verdict.castcourt.domain.speaking.repository.SpeakingRepository;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/* 판결 저장 : 진행 중 재판(메모리) 한 판을 trial 1행 + speaking 여러 행으로 한 트랜잭션에 저장한다
 *  TrialService 와 다른 빈으로 둔 이유 : @Transactional 은 스프링이 빈을 감싼 대리 객체(프록시)를 거칠 때만 동작한다.
 *  같은 클래스 안에서 this.save() 로 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.
 * */
@Component
public class TrialVerdictWriter {

    private final TrialRepository trialRepository;
    private final SpeakingRepository speakingRepository;

    @Autowired
    public TrialVerdictWriter(TrialRepository trialRepository, SpeakingRepository speakingRepository) {
        this.trialRepository = trialRepository;
        this.speakingRepository = speakingRepository;
    }

    /* 저장 · 중간에 하나라도 실패하면 trial · speaking 모두 저장되지 않는다 (롤백)
     *  UNIQUE(user_id, work_case_id, role) 위반은 DataIntegrityViolationException 으로 밖에 나간다 → TrialService 가 409 로 바꾼다
     * */
    @Transactional
    public TrialEntity save(TrialSession session, int penaltyTier, String judgeSummary) {

        TrialEntity trial = trialRepository.save(new TrialEntity(
                session.getWorkCaseId(),
                session.getUserId(),
                session.getRole(),
                session.getMode(),
                session.usedCardIds(),      // 실제로 공방에 쓴 카드만 (3번째를 안 썼으면 2장)
                session.getScore(),
                penaltyTier,
                judgeSummary,
                session.getSnapshot()
        ));

        // 메모리의 발언 기록지 한 줄 = speaking 한 행 (seq 순서 그대로)
        List<SpeakingEntity> speakings = session.getSpeakings().stream()
                .map(s -> new SpeakingEntity(trial, s.seq(), s.stage(), s.side(), s.speaker(), s.content(),
                        s.issue(), s.evidence(), s.rebut(), s.novel(), s.grade(), s.delta(), s.scoreAfter()))
                .toList();
        speakingRepository.saveAll(speakings);

        return trial;
    }
}
