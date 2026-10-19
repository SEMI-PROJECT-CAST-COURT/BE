package com.verdict.castcourt.domain.workcase.repository;

import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
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
                    "SELECT COUNT(DISTINCT t.id) " +
                    "FROM trial t " +
                    "JOIN work_case wc ON wc.id = t.work_case_id " +
                    "WHERE t.user_id = :userId " +
                    "AND wc.work_id = :workId " +
                    "AND wc.is_usage = true" +
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
            "WHERE ec.workCase.workId = :workId " +
            "AND ec.workCase.isUsage = true"
    )
    List<EvidenceCardEntity> findEvidenceCardsByWorkId(@Param("workId") Integer workId);
}
