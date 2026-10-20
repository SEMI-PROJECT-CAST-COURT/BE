package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/* TrialCaseReader 임시 구현 : 다른 팀원 엔티티를 쓰지 않고 테이블을 SQL 로 직접 읽는다 */
@Repository
public class TrialCaseJdbcReader implements TrialCaseReader {

    private final JdbcClient jdbcClient;

    @Autowired
    public TrialCaseJdbcReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<CaseSnapshot> findSnapshot(int caseId) {

        // 카드 · 형벌표를 먼저 읽는다 (사건이 없으면 빈 목록이고, 아래에서 빈 Optional 이 된다)
        List<CaseSnapshot.Card> cards = findCards(caseId);
        List<CaseSnapshot.Penalty> penalties = findPenalties(caseId);

        // 사건 + 작품 제목 (사건 · 작품 둘 다 삭제되지 않은 것만)
        return jdbcClient.sql("""
                        SELECT wc.id, wc.work_id, w.title, wc.question, wc.suspect, wc.victim, wc.summary, wc.progress
                        FROM work_case wc
                        JOIN work w ON w.id = wc.work_id
                        WHERE wc.id = :caseId
                          AND wc.is_usage = TRUE
                          AND w.is_usage = TRUE
                        """)
                .param("caseId", caseId)
                .query((rs, rowNum) -> new CaseSnapshot(
                        rs.getInt("id"),
                        rs.getInt("work_id"),
                        rs.getString("title"),
                        rs.getString("question"),
                        rs.getString("suspect"),
                        rs.getString("victim"),
                        rs.getString("summary"),
                        rs.getString("progress"),
                        cards,
                        penalties
                ))
                .optional();
    }

    // 근거 카드 (양측 전부)
    private List<CaseSnapshot.Card> findCards(int caseId) {
        return jdbcClient.sql("""
                        SELECT id, side, title, body, weight
                        FROM evidence_card
                        WHERE work_case_id = :caseId
                        ORDER BY id
                        """)
                .param("caseId", caseId)
                .query((rs, rowNum) -> new CaseSnapshot.Card(
                        rs.getInt("id"),
                        Side.valueOf(rs.getString("side")),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getInt("weight")
                ))
                .list();
    }

    // 형벌표 6단계
    private List<CaseSnapshot.Penalty> findPenalties(int caseId) {
        return jdbcClient.sql("""
                        SELECT tier, sentence
                        FROM penalty_tier
                        WHERE work_case_id = :caseId
                        ORDER BY tier
                        """)
                .param("caseId", caseId)
                .query((rs, rowNum) -> new CaseSnapshot.Penalty(
                        rs.getInt("tier"),
                        rs.getString("sentence")
                ))
                .list();
    }
}