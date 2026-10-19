package com.verdict.castcourt.domain.workcase.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "work_case")
public class WorkCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // PK
    private Long workId;                // 작품 id TODO: (추후 Work 개발 시, 참조 관계로 변경)
    String question;                    // 쟁점
    String suspect;                     // 피의자
    String victim;                      // 피해자
    String summary;                     // 사건 개요
    String progress;                    // 사건 경과
    boolean isUsage = true;                        // 삭제 여부

    public WorkCaseEntity() {}

    public WorkCaseEntity(Long id, Long workId, String question, String suspect, String victim, String summary, String progress, boolean isUsage) {
        this.id = id;
        this.workId = workId;
        this.question = question;
        this.suspect = suspect;
        this.victim = victim;
        this.summary = summary;
        this.progress = progress;
        this.isUsage = isUsage;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkId() {
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
}
