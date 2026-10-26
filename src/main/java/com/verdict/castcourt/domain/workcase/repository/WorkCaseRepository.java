package com.verdict.castcourt.domain.workcase.repository;

import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkCaseRepository extends JpaRepository<WorkCaseEntity, Integer> {

    List<WorkCaseEntity> getWorkCasesByWorkId(Integer workId);

    /* 특정 작품의 진행된 사건 개수: 진행도(progress) 계산에 사용 */
    @Query(
            value = "" +
                    "SELECT COUNT(DISTINCT wc.id) " +
                    "FROM trial t " +
                    "JOIN work_case wc ON wc.id = t.work_case_id " +
                    "WHERE t.user_id = :userId " +
                    "AND wc.work_id = :workId " +
                    "AND wc.is_usage = TRUE" +
                    "",
            nativeQuery = true
    )
    int countDoneWorkCases(@Param("workId") Integer workId,
                           @Param("userId") Integer userId);

    /* 재판 진행 기록 존재 여부 */
    @Query(
            value = "" +
                    "SELECT COUNT(id) " +
                    "FROM trial " +
                    "WHERE user_id = :userId " +
                    "AND work_case_id = :caseId " +
                    "AND role = :role " +
                    "",
            nativeQuery = true
    )
    long existsByUserIdAndCaseIdAndRole(@Param("caseId") Integer caseId,
                                        @Param("userId") Integer userId,
                                        @Param("role") String role);

    /* 특정 작품에 속한 사건들의 근거 카드 전체 조회 - 목록 난이도 계산용 */
    @Query(
            "SELECT ec FROM EvidenceCardEntity ec " +
                    "WHERE ec.workCase.work.id = :workId " +
                    "AND ec.workCase.isUsage = true"
    )
    List<EvidenceCardEntity> findEvidenceCardsByWorkId(@Param("workId") Integer workId);

    /* 특정 작품에 속한 사건들의 형벌표 전체 조회 - 목록 조회용 */
    @Query(
            "SELECT pt FROM PenaltyTierEntity pt " +
            "WHERE pt.workCase.work.id = :workId " +
            "AND pt.workCase.isUsage = true " +
            "ORDER BY pt.tier"
    )
    List<PenaltyTierEntity> findPenaltiesByWorkId(@Param("workId") Integer workId);

    /* 특정 작품에 속한 사건들의 내 재판 기록 전체 조회 - 목록 내 판결 조회용 */
    @Query(
            "SELECT t FROM TrialEntity t, WorkCaseEntity wc " +
            "WHERE wc.id = t.workCaseId " + // 재판과 사건 테이블의 JOIN 조건
            "AND wc.work.id = :workId " +    // 특정 작품에 속하면서
            "AND wc.isUsage = true " +      // 삭제되지 않은 사건에 대한
            "AND t.userId = :userId"        // 특정 유저의
    )
    List<TrialEntity> findMyTrialsByWorkId(@Param("workId") Integer workId,
                                           @Param("userId") Integer userId);
}
