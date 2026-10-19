package com.verdict.castcourt.domain.penaltytier.dto;

public class PenaltyTierDTO {
    private int tier;
    private String sentence;

    public PenaltyTierDTO() {}

    public PenaltyTierDTO(int tier, String sentence) {
        this.tier = tier;
        this.sentence = sentence;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }
}
