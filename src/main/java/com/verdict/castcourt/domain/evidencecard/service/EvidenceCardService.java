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

        long prosecutionCount = cards.stream().filter(e -> "PROSECUTION".equals(e.getSide())).count();
        long defenseCount = cards.stream().filter(e -> "DEFENSE".equals(e.getSide())).count();

        // 진영별 3장 이상
        if (prosecutionCount < 3 || defenseCount < 3) {
            throw new BusinessException(ErrorCode.CASE_CARD_COUNT_INSUFFICIENT);
        }

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

    @Transactional
    public void updateEvidenceCards(WorkCaseEntity workCase, List<EvidenceCardDTO> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        validateCards(cards);

        if (cards.stream().anyMatch(e -> e.getId() != null && e.getId() < 0)) {
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        }

        List<EvidenceCardDTO> saveList = cards.stream()
                .filter(e -> e.getId() == null || e.getId() == 0).toList();

        List<EvidenceCardDTO> updateList = cards.stream()
                .filter(e -> e.getId() != null && e.getId() > 0).toList();

        List<Integer> ids = updateList.stream().map(EvidenceCardDTO::getId).distinct().toList();
        if (ids.size() != updateList.size()) {
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        }

        Map<Integer, EvidenceCardEntity> cardMap = evidenceCardRepository
                .findByIdInAndWorkCaseId(ids, workCase.getId()).stream()
                .collect(Collectors.toMap(EvidenceCardEntity::getId, Function.identity()));

        if (cardMap.size() != ids.size()) {
            throw new BusinessException(ErrorCode.EVIDENCE_CARD_NOT_FOUND);
        }

        for (EvidenceCardDTO card : updateList) {
            cardMap.get(card.getId()).update(card.getTitle(), card.getBody(), card.getWeight());
        }

        List<EvidenceCardEntity> evidenceCards = saveList
                .stream()
                .map(e -> new EvidenceCardEntity(
                        workCase,
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

        for (EvidenceCardDTO card : cards) {
            String side = card.getSide();

            // side: PROSECUTION / DEFENSE만 허용
            if (!"PROSECUTION".equals(side) && !"DEFENSE".equals(side)) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // title: 1~20자
            if (card.getTitle() == null || card.getTitle().isBlank() || card.getTitle().length() > 20) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // body: 1~100자
            if (card.getBody() == null || card.getBody().isBlank() || card.getBody().length() > 100) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            // weight: 1~20
            if (card.getWeight() == null ||card.getWeight() < 1 || card.getWeight() > 20) {
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
