package com.verdict.castcourt.domain.evidencecard.service;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardUpdateDTO;
import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.evidencecard.repository.EvidenceCardRepository;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Transactional
    public void updateEvidenceCards(int caseId, List<EvidenceCardUpdateDTO> cards) {
        validateUpdateCards(cards);

        List<Integer> ids = cards.stream().map(EvidenceCardUpdateDTO::getId).distinct().toList();
        if (ids.size() != cards.size()) {
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        }

        Map<Integer, EvidenceCardEntity> cardMap = evidenceCardRepository
                .findByIdInAndWorkCaseId(ids, caseId).stream()
                .collect(Collectors.toMap(EvidenceCardEntity::getId, Function.identity()));

        if (cardMap.size() != ids.size()) {
            throw new BusinessException(ErrorCode.EVIDENCE_CARD_NOT_FOUND);
        }

        for (EvidenceCardUpdateDTO card : cards) {
            cardMap.get(card.getId()).update(card.getTitle(), card.getBody(), card.getWeight());
        }
    }

    private void validateUpdateCards(List<EvidenceCardUpdateDTO> cards) {
        if (cards == null || cards.isEmpty()) {
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        }
        for (EvidenceCardUpdateDTO card : cards) {
            if (card.getId() == null || card.getId() < 1) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }
            String title = card.getTitle();
            if (title != null && (title.isBlank() || title.length() > 20)) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }
            String body = card.getBody();
            if (body != null && (body.isBlank() || body.length() > 70)) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }
            Integer weight = card.getWeight();
            if (weight != null && (weight < 1 || weight > 20)) {
                throw new BusinessException(ErrorCode.CARD_WEIGHT_INVALID);
            }
        }
    }

    @Transactional
    public void deleteEvidenceCards(int caseId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        Set<Integer> idSet = new HashSet<>(ids);

        List<EvidenceCardEntity> allCards = evidenceCardRepository.findByWorkCaseId(caseId);

        List<EvidenceCardEntity> toDelete = allCards.stream()
                .filter(card -> idSet.contains(card.getId()))
                .toList();

        if (toDelete.size() != idSet.size()) {
            throw new BusinessException(ErrorCode.EVIDENCE_CARD_NOT_FOUND);
        }

        long prosecutionLeft = allCards.stream()
                .filter(card -> !idSet.contains(card.getId()))
                .filter(card -> "PROSECUTION".equals(card.getSide()))
                .count();
        long defenseLeft = allCards.stream()
                .filter(card -> !idSet.contains(card.getId()))
                .filter(card -> "DEFENSE".equals(card.getSide()))
                .count();

        if (prosecutionLeft < 3 || defenseLeft < 3) {
            throw new BusinessException(ErrorCode.CASE_CARD_COUNT_INSUFFICIENT);
        }

        evidenceCardRepository.deleteAll(toDelete);
    }
}
