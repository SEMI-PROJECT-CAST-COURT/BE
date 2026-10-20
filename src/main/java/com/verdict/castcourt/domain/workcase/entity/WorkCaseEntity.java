package com.verdict.castcourt.domain.workcase.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "work_case")
public class WorkCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;                    // PK

    @Column(name = "work_id")
    private int workId;                // 작품 id TODO: (추후 Work 개발 시, 참조 관계로 변경)

    @Column(name = "question")
    private String question;                    // 쟁점

    @Column(name = "suspect")
    private String suspect;                     // 피의자

    @Column(name = "victim")
    private String victim;                      // 피해자

    @Column(name = "summary")
    private String summary;                     // 사건 개요

    @Column(name = "progress")
    private String progress;                    // 사건 경과

    @Column(name = "is_usage")
    private boolean isUsage = true;                        // 삭제 여부

    public WorkCaseEntity() {}

    public WorkCaseEntity(int workId, String question, String suspect, String victim, String summary, String progress, boolean isUsage) {
        this.workId = workId;
        this.question = question;
        this.suspect = suspect;
        this.victim = victim;
        this.summary = summary;
        this.progress = progress;
        this.isUsage = isUsage;
    }

    public int getId() {
        return id;
    }

    public int getWorkId() {
        return workId;
    }

    public String getQuestion() {
        return question;
    }

    public String getSuspect() {
        return suspect;
    }

    public String getVictim() {
        return victim;
    }

    public String getSummary() {
        return summary;
    }

    public String getProgress() {
        return progress;
    }

    public boolean isUsage() {
        return isUsage;
    }

    public void update(String question, String suspect, String victim, String summary, String progress) {
        if(question != null) this.question = question;
        if(suspect != null) this.suspect = suspect;
        if(victim != null) this.victim = victim;
        if(summary != null) this.summary = summary;
        if(progress != null) this.progress = progress;
    }
}
