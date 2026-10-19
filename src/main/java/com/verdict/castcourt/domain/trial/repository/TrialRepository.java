package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrialRepository extends JpaRepository<TrialEntity, Integer> {

    /* 내 재판 목록 (trialId 내림차순 · cursor 보다 작은 id 부터 size 개)
     *  - 필터 값이 null 이면 그 조건은 건너뛴다
     *  - 승리 조건은 ScoreRule.isWin 과 같다 : 변호인은 형벌 단계 1~3, 검사는 4~6
     *  - work_case · work 엔티티가 아직 없어서 네이티브 SQL 로 is_usage 만 읽는다
     * */
    // 이 사건 · 역할로 판결 난 재판이 있는지 (재판 시작 409 검사)
    boolean existsByUserIdAndWorkCaseIdAndRole(Integer userId, Integer workCaseId, Side role);
    @Query(value = """
            SELECT t.id                              AS trialId,
                   t.case_snapshot ->> '$.workTitle' AS workTitle,
                   t.case_snapshot ->> '$.question'  AS question,
                   t.role                            AS role,
                   t.penalty_tier                    AS penaltyTier,
                   wc.is_usage                       AS caseUsage,
                   w.is_usage                        AS workUsage
            FROM trial t
            JOIN work_case wc ON wc.id = t.work_case_id
            JOIN work w ON w.id = wc.work_id
            WHERE t.user_id = :userId
              AND (:role IS NULL OR t.role = :role)
              AND (:workId IS NULL OR wc.work_id = :workId)
              AND (:outcome IS NULL
                   OR (:outcome = 'WIN' AND ((t.role = 'DEFENSE' AND t.penalty_tier <= 3)
                                          OR (t.role = 'PROSECUTION' AND t.penalty_tier >= 4)))
                   OR (:outcome = 'LOSE' AND ((t.role = 'DEFENSE' AND t.penalty_tier >= 4)
                                           OR (t.role = 'PROSECUTION' AND t.penalty_tier <= 3))))
              AND (:cursor IS NULL OR t.id < :cursor)
            ORDER BY t.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<MyTrialRow> findMyTrials(@Param("userId") int userId,
                                  @Param("role") String role,
                                  @Param("outcome") String outcome,
                                  @Param("workId") Integer workId,
                                  @Param("cursor") Integer cursor,
                                  @Param("size") int size);
}
