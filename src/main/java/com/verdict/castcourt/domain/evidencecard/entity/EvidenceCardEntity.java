package com.verdict.castcourt.domain.evidencecard.entity;

import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "evidence_card")
public class EvidenceCardEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_case_id")
    private WorkCaseEntity workCase;

    @Column(name = "side")
    private String side;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "weight")
    private int weight;

    public EvidenceCardEntity() {}

    public EvidenceCardEntity(int id, WorkCaseEntity workCase, String side, String title, String body, int weight) {
        this.id = id;
        this.workCase = workCase;
        this.side = side;
        this.title = title;
        this.body = body;
        this.weight = weight;
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

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
