package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrialRepository extends JpaRepository<TrialEntity, Integer> {

    // 이 사건 · 역할로 판결 난 재판이 있는지 (재판 시작 409 검사)
    // TrialEntity 의 읽기 전용 userId · workCaseId 필드로 찾는다 (사건 · 계정 테이블을 JOIN 하지 않음)
    boolean existsByUserIdAndWorkCaseIdAndRole(Integer userId, Integer workCaseId, Side role);

    // 이 사용자 · 이 사건의 재판 (역할마다 최대 1개라 0~2개 · 사건 기록 요약)
    List<TrialEntity> findByUserIdAndWorkCaseId(Integer userId, Integer workCaseId);

    // (역할, 형벌 단계)별 판 수 (내 통계) · 판이 없는 칸은 줄이 나오지 않는다
    @Query("""
            SELECT t.role        AS role,
                   t.penaltyTier AS penaltyTier,
                   COUNT(t)      AS trialCount
            FROM TrialEntity t
            WHERE t.userId = :userId
            GROUP BY t.role, t.penaltyTier
            """)
    List<TrialStatRow> countByRoleAndTier(@Param("userId") int userId);

    /* 내 재판 목록 (trialId 내림차순 · cursor 보다 작은 id 부터 limit 개)
     *  - 필터 값이 null 이면 그 조건은 건너뛴다
     *  - 승리 조건은 ScoreRule.isWin 과 같다 : 변호인은 형벌 단계 1~3, 검사는 4~6
     *  - 사건에는 작품이 id 숫자(workId)로만 있어서 작품은 ON 조건으로 직접 잇는다
     * */
    @Query("""
            SELECT t.id           AS trialId,
                   t.caseSnapshot AS caseSnapshot,
                   t.role         AS role,
                   t.penaltyTier  AS penaltyTier,
                   wc.isUsage     AS caseUsage,
                   w.isUsage      AS workUsage
            FROM TrialEntity t
            JOIN t.workCase wc
            JOIN Work w ON w.id = wc.workId
            WHERE t.user.id = :userId
              AND (:role IS NULL OR t.role = :role)
              AND (:workId IS NULL OR wc.workId = :workId)
              AND (:outcome IS NULL
                   OR (:outcome = 'WIN' AND ((t.role = DEFENSE AND t.penaltyTier <= 3)
                                          OR (t.role = PROSECUTION AND t.penaltyTier >= 4)))
                   OR (:outcome = 'LOSE' AND ((t.role = DEFENSE AND t.penaltyTier >= 4)
                                           OR (t.role = PROSECUTION AND t.penaltyTier <= 3))))
              AND (:cursor IS NULL OR t.id < :cursor)
            ORDER BY t.id DESC
            """)
    List<MyTrialRow> findMyTrials(@Param("userId") int userId,
                                  @Param("role") Side role,
                                  @Param("outcome") String outcome,
                                  @Param("workId") Integer workId,
                                  @Param("cursor") Integer cursor,
                                  Limit limit);
}
