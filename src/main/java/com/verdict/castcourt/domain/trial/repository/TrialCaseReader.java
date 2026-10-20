package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.evidencecard.repository.EvidenceCardRepository;
import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import com.verdict.castcourt.domain.penaltytier.repository.PenaltyTierRepository;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.domain.workcase.repository.WorkCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/* 재판에 필요한 사건 · 작품 · 카드 · 형벌표를 팀원 리포지토리로 읽어 스냅샷 하나로 묶는다 */
@Component
public class TrialCaseReader {

    private final WorkCaseRepository workCaseRepository;
    private final WorkRepository workRepository;
    private final EvidenceCardRepository evidenceCardRepository;
    private final PenaltyTierRepository penaltyTierRepository;

    @Autowired
    public TrialCaseReader(WorkCaseRepository workCaseRepository,
                           WorkRepository workRepository,
                           EvidenceCardRepository evidenceCardRepository,
                           PenaltyTierRepository penaltyTierRepository) {
        this.workCaseRepository = workCaseRepository;
        this.workRepository = workRepository;
        this.evidenceCardRepository = evidenceCardRepository;
        this.penaltyTierRepository = penaltyTierRepository;
    }

    /* 삭제되지 않은 사건이면 스냅샷, 없거나 사건 · 작품이 삭제됐으면 빈 Optional */
    public Optional<CaseSnapshot> findSnapshot(int caseId) {

        Optional<WorkCaseEntity> workCase = workCaseRepository.findById(caseId)
                .filter(WorkCaseEntity::isUsage);
        if (workCase.isEmpty()) {
            return Optional.empty();
        }

        // 작품은 사건에 id 숫자로만 붙어 있어서 따로 읽는다 (findActiveById = 삭제되지 않은 작품만)
        Optional<Work> work = workRepository.findActiveById(workCase.get().getWorkId());
        if (work.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(toSnapshot(workCase.get(), work.get()));
    }

    private CaseSnapshot toSnapshot(WorkCaseEntity workCase, Work work) {
        return new CaseSnapshot(
                workCase.getId(),
                work.getId(),
                work.getTitle(),
                workCase.getQuestion(),
                workCase.getSuspect(),
                workCase.getVictim(),
                workCase.getSummary(),
                workCase.getProgress(),
                findCards(workCase.getId()),
                findPenalties(workCase.getId())
        );
    }

    // 근거 카드 (양측 전부 · id 순)
    private List<CaseSnapshot.Card> findCards(int caseId) {
        return evidenceCardRepository.findByWorkCaseId(caseId).stream()
                .sorted(Comparator.comparingInt(EvidenceCardEntity::getId))
                .map(card -> new CaseSnapshot.Card(
                        card.getId(),
                        Side.valueOf(card.getSide()),      // 카드의 측은 문자열로 저장돼 있어 enum 으로 바꾼다
                        card.getTitle(),
                        card.getBody(),
                        card.getWeight()
                ))
                .toList();
    }

    // 형벌표 6단계 (tier 순)
    private List<CaseSnapshot.Penalty> findPenalties(int caseId) {
        return penaltyTierRepository.findByWorkCaseId(caseId).stream()
                .sorted(Comparator.comparingInt(PenaltyTierEntity::getTier))
                .map(penalty -> new CaseSnapshot.Penalty(penalty.getTier(), penalty.getSentence()))
                .toList();
    }
}