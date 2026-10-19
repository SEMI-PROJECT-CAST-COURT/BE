package com.verdict.castcourt.domain.workcase.repository;

import com.verdict.castcourt.domain.workcase.dto.projection.WorkCaseCardRow;
import com.verdict.castcourt.domain.workcase.dto.projection.WorkCasePenaltyRow;
import com.verdict.castcourt.domain.workcase.dto.projection.WorkCaseWeightSumRow;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkCaseRepository extends JpaRepository<WorkCaseEntity, Integer> { ;

    List<WorkCaseEntity> getWorkCasesByWorkId(Long workId);

    /* 특정 작품의 진행된 사건 개수: 진행도(progress) 계산에 사용 */
    @Query(
            value = "" +
                    "SELECT COUNT(t.id) " +
                    "FROM trial t " +
                    "JOIN work_case wc ON wc.id = t.work_case_id " +
                    "WHERE t.user_id = :userId " +
                    "AND wc.work_id = :workId" +
                    "",
            nativeQuery = true
    )
    int countDoneWorkCases(@Param("workId") Long workId,
                           @Param("userId") Long userId);

    /* 재판 진행 기록 존재 여부 */
    @Query(
            value = "" +
                    "SELECT COUNT(id) " +
                    "FROM trial " +
                    "WHERE user_id = :userId " +
                    "AND work_case_id = :caseId " +
                    "AND role = :role" +
                    "",
            nativeQuery = true
    )
    long existsByUserIdAndCaseIdAndRole(@Param("caseId") Long caseId,
                                        @Param("userId") Long userId,
                                        @Param("role") String role);

    // TODO: 추후에 service 코드로 교체 - 찬웅님
    /* 특정 사건의 근거카드 목록 조회 */
    @Query(
            value = "" +
                    "SELECT side, title, body, weight " +
                    "FROM evidence_card " +
                    "WHERE work_case_id = :caseId" +
                    "",
            nativeQuery = true
    )
    List<WorkCaseCardRow> findCardsByCaseId(@Param("caseId") Long caseId);

    // TODO: 추후에 service 코드로 교체 - 찬웅님
    /* 특정 사건의 형벌표 조회 */
    @Query(
            value = "" +
                    "SELECT tier, sentence " +
                    "FROM penalty_tier " +
                    "WHERE work_case_id = :caseId " +
                    "ORDER BY tier",
            nativeQuery = true
    )
    List<WorkCasePenaltyRow> findPenaltiesByCaseId(@Param("caseId") Long caseId);

    /* 목록 난이도 - 변호사의 근거 카드 가중치 합, 검사의 근거카드 가중치 합 */
    @Query(
            value = "" +
                    "SELECT ec.work_case_id AS caseId, " +
                    "COALESCE(SUM(CASE WHEN ec.side = 'DEFENSE' THEN ec.weight END), 0) AS def, " +
                    "COALESCE(SUM(CASE WHEN ec.side = 'PROSECUTION' THEN ec.weight END), 0) AS pro " +
                    "FROM evidence_card ec " +
                    "JOIN work_case wc ON wc.id = ec.work_case_id " +
                    "WHERE wc.work_id = :workId " +
                    "GROUP BY ec.work_case_id" +
                    "",
            nativeQuery = true
    )
    List<WorkCaseWeightSumRow> sumWeightByWorkId(@Param("workId") Long workId);
}
