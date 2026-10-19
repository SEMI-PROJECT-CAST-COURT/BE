package com.verdict.castcourt.domain.evidencecard.service;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.repository.EvidenceCardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .map(e -> new EvidenceCardDTO(e.getSide(), e.getTitle(), e.getBody(), e.getWeight()))
                .toList();

        return new EvidenceCardResponseDTO(cards);
    }
}
