package com.verdict.castcourt.domain.evidencecard.dto;

import java.util.List;

public class EvidenceCardResponseDTO {
    private List<EvidenceCardDTO> evidenceCards;
    private int prosecutionTotalWeight;
    private int defenseTotalWeight;

    public EvidenceCardResponseDTO() {}

    public EvidenceCardResponseDTO(List<EvidenceCardDTO> evidenceCards) {
        this.evidenceCards = evidenceCards;
        this.prosecutionTotalWeight = evidenceCards.stream()
                .filter(e -> "PROSECUTION".equals(e.getSide()))
                .mapToInt(EvidenceCardDTO::getWeight).sum();
        this.defenseTotalWeight = evidenceCards.stream()
                .filter(e -> "DEFENSE".equals(e.getSide()))
                .mapToInt(EvidenceCardDTO::getWeight).sum();
    }

    public List<EvidenceCardDTO> getEvidenceCards() {
        return evidenceCards;
    }

    public void setEvidenceCards(List<EvidenceCardDTO> evidenceCards) {
        this.evidenceCards = evidenceCards;
    }

    public int getProsecutionTotalWeight() {
        return prosecutionTotalWeight;
    }

    public int getDefenseTotalWeight() {
        return defenseTotalWeight;
    }
}
