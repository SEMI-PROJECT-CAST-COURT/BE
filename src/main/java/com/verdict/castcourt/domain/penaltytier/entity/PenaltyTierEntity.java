package com.verdict.castcourt.domain.penaltytier.entity;

import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "penalty_tier")
public class PenaltyTierEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_case_id")
    private WorkCaseEntity workCase;

    @Column(name = "tier")
    private int tier;

    @Column(name = "sentence")
    private String sentence;

    public PenaltyTierEntity() {}

    public PenaltyTierEntity(int id, int tier, String sentence) {
        this.id = id;
        this.tier = tier;
        this.sentence = sentence;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public WorkCaseEntity getWorkCase() {
        return workCase;
    }

    public void setWorkCase(WorkCaseEntity workCase) {
        this.workCase = workCase;
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
