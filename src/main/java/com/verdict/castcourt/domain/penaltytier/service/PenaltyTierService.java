package com.verdict.castcourt.domain.penaltytier.service;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import com.verdict.castcourt.domain.penaltytier.repository.PenaltyTierRepository;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PenaltyTierService {
    private final PenaltyTierRepository penaltyTierRepository;

    @Autowired
    public PenaltyTierService(PenaltyTierRepository penaltyTierRepository) {
        this.penaltyTierRepository = penaltyTierRepository;
    }

    @Transactional(readOnly = true)
    public List<PenaltyTierDTO> findPenaltyTierByWorkCaseId(int workCaseId) {
        return penaltyTierRepository.findByWorkCaseId(workCaseId)
                .stream()
                .map(p -> new PenaltyTierDTO(p.getTier(), p.getSentence()))
                .toList();
    }

    @Transactional
    public void savePenaltyTier(WorkCaseEntity workCaseEntity, List<PenaltyTierDTO> penaltyTierDTO) {
        validatePenalties(penaltyTierDTO);

        List<PenaltyTierEntity> penalties = penaltyTierDTO
                .stream()
                .map(p -> new PenaltyTierEntity(
                        workCaseEntity,
                        p.getTier(),
                        p.getSentence()
                ))
                .toList();

        penaltyTierRepository.saveAll(penalties);
    }

    private void validatePenalties(List<PenaltyTierDTO> penalties) {
        // 형벌표는 정확히 6단계
        if (penalties == null || penalties.size() != 6) {
            throw new BusinessException(ErrorCode.PENALTY_TIER_COUNT_INVALID);
        }

        boolean[] registered = new boolean[7];   // index 1~6 사용

        for (PenaltyTierDTO penalty : penalties) {
            int tier = penalty.getTier();

            // tier: 1~6
            if (tier < 1 || tier > 6) {
                throw new BusinessException(ErrorCode.PENALTY_TIER_COUNT_INVALID);
            }

            // tier 중복 불가 (1~6이 한 번씩)
            if (registered[tier]) {
                throw new BusinessException(ErrorCode.PENALTY_TIER_COUNT_INVALID);
            }
            registered[tier] = true;

            // sentence: 1~100자
            String sentence = penalty.getSentence();
            if (sentence == null || sentence.isBlank() || sentence.length() > 50) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }
        }
    }

    @Transactional
    public void updatePenaltyTiers(int caseId, List<PenaltyTierDTO> penalties) {
        if (penalties == null || penalties.isEmpty()) {
            return;
        }

        // 사건의 형벌표 6개를 tier 기준 Map으로
        Map<Integer, PenaltyTierEntity> tierMap = penaltyTierRepository.findByWorkCaseId(caseId).stream()
                .collect(Collectors.toMap(PenaltyTierEntity::getTier, Function.identity()));

        Set<Integer> seen = new HashSet<>();
        for (PenaltyTierDTO penalty : penalties) {
            int tier = penalty.getTier();

            if (tier < 1 || tier > 6 || !seen.add(tier)) {
                throw new BusinessException(ErrorCode.PENALTY_TIER_COUNT_INVALID);
            }

            String sentence = penalty.getSentence();
            if (sentence == null || sentence.isBlank() || sentence.length() > 50) {
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
            }

            PenaltyTierEntity entity = tierMap.get(tier);
            if (entity == null) {
                throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
            }
            entity.updateSentence(sentence);
        }
    }
}
