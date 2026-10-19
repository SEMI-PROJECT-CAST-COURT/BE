package com.verdict.castcourt.domain.speaking.entity;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.Trial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* 발언 (speaking) : 재판 한 판 안의 발언 하나와 판사 판정
 *  판결 때 trial 과 같은 트랜잭션으로 한꺼번에 저장된다. (7장)
 * */
@Entity
@Table(name = "speaking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Speaking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 이 발언이 속한 재판 (필요할 때만 읽어 오도록 LAZY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_id", nullable = false)
    private Trial trial;

    // 재판 안에서의 순서 1~16 (카드 3장이면 22)
    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 12)
    private Stage stage;

    // 발언한 쪽
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 12)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker", nullable = false, length = 6)
    private Speaker speaker;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    // 판사 판정 4항목 : ① 쟁점 ② 근거 ③ 반박(첫 발언이면 null) ④ 새로움
    @Column(name = "issue", nullable = false)
    private boolean issue;

    @Column(name = "evidence", nullable = false)
    private boolean evidence;

    @Column(name = "rebut")
    private Boolean rebut;

    @Column(name = "novel", nullable = false)
    private boolean novel;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 8)
    private Grade grade;

    // 게이지 변화량 (검사 +, 변호인 -)
    @Column(name = "delta", nullable = false)
    private int delta;

    // 이 발언 뒤 게이지
    @Column(name = "score_after", nullable = false)
    private int scoreAfter;
}