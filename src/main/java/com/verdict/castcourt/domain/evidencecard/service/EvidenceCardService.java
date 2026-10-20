package com.verdict.castcourt.domain.evidencecard.service;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.evidencecard.repository.EvidenceCardRepository;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvidenceCardService {
    private final EvidenceCardRepository evidenceCardRepository;

    @Autowired
    public EvidenceCardService(EvidenceCardRepository evidenceCardRepository) {
        this.evidenceCardRepository = evidenceCardRepository;
    }

    @Transactional(readOnly = true)
    public EvidenceCardResponseDTO findEvidenceCardsByWorkCaseId(int workCaseId) {
        List<EvidenceCardDTO> cards = evidenceCardRepository.findByWorkCaseId(workCaseId)
                .stream()
                .map(e -> new EvidenceCardDTO(e.getId(), e.getSide(), e.getTitle(), e.getBody(), e.getWeight()))
                .toList();

        return new EvidenceCardResponseDTO(cards);
    }

    @Transactional
    public void saveEvidenceCard(WorkCaseEntity workCaseEntity, List<EvidenceCardDTO> cards) {
        validateCards(cards);

        List<EvidenceCardEntity> evidenceCards = cards
                .stream()
                .map(e -> new EvidenceCardEntity(
                        workCaseEntity,
                        e.getSide(),
                        e.getTitle(),
                        e.getBody(),
                        e.getWeight()
                ))
                .toList();

        evidenceCardRepository.saveAll(evidenceCards);
    }

    private void validateCards(List<EvidenceCardDTO> cards) {
        if (cards == null || cards.isEmpty()) {
            throw new BusinessException(ErrorCode.CASE_CARD_COUNT_INSUFFICIENT);
        }

        int prosecutionCount = 0;
        int defenseCount = 0;

        for (EvidenceCardDTO card : cards) {
            String side = card.getSide();

            // side: PROSECUTION / DEFENSE만 허용
            if ("PROSECUTION".equals(side)) {
                prosecutionCount++;
            } else if ("DEFENSE".equals(side)) {
                defenseCount++;
            } else {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // title: 1~20자
            if (card.getTitle() == null || card.getTitle().isBlank() || card.getTitle().length() > 20) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // body: 1~70자
            if (card.getBody() == null || card.getBody().isBlank() || card.getBody().length() > 70) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // weight: 1~20
            if (card.getWeight() < 1 || card.getWeight() > 20) {
                throw new BusinessException(ErrorCode.CARD_WEIGHT_INVALID);
            }
        }

        // 진영별 3장 이상
        if (prosecutionCount < 3 || defenseCount < 3) {
            throw new BusinessException(ErrorCode.CASE_CARD_COUNT_INSUFFICIENT);
        }
    }
}
