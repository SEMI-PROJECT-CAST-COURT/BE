package com.verdict.castcourt.domain.trial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/* 재판 (trial) : 판결이 난 재판 한 판의 결과
 *  진행 중인 재판은 서버 메모리에 있고, 판결이 나는 순간 이 엔티티로 저장된다. (7장)
 * */
@Entity
@Table(name = "trial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 다른 팀원의 엔티티(WorkCase, Account)를 쓰지 않고 id 숫자만 들고 있는다
    @Column(name = "work_case_id", nullable = false)
    private Integer workCaseId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    // 사용자가 맡은 역할
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 12)
    private Side role;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 12)
    private TrialMode mode;

    // 공방에 뽑힌 검사 카드 id (2~3장, 뽑힌 순서)
    @Convert(converter = CardIdsConverter.class)
    @Column(name = "card_ids", nullable = false)
    private List<Integer> cardIds;

    // 최종 게이지 0~100
    @Column(name = "score", nullable = false)
    private int score;

    // 형벌 단계 1~6
    @Column(name = "penalty_tier", nullable = false)
    private int penaltyTier;

    // 판사 총평
    @Column(name = "judge_summary", nullable = false, length = 1000)
    private String judgeSummary;

    // 재판 시작 때의 사건 복사본
    @Convert(converter = CaseSnapshotConverter.class)
    @Column(name = "case_snapshot", nullable = false)
    private CaseSnapshot caseSnapshot;
}