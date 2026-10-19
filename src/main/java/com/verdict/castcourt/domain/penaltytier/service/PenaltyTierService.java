package com.verdict.castcourt.domain.penaltytier.service;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.penaltytier.repository.PenaltyTierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
