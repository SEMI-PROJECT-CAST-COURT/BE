package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;

import java.util.List;

/* AI 부품 테스트용 재판 한 판 : 검사 카드 11(가중치 8) · 12(가중치 12), 변호인 카드 21(가중치 5) */
final class SpeakingAiFixture {

    private SpeakingAiFixture() {
    }

    static TrialSession session(Side role) {

        CaseSnapshot snapshot = new CaseSnapshot(
                7, 1, "작품", "쟁점 질문", "피의자", "피해자", "개요", "경과",
                List.of(
                        new CaseSnapshot.Card(11, Side.PROSECUTION, "검사 카드 1", "내용 1", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "검사 카드 2", "내용 2", 12),
                        new CaseSnapshot.Card(21, Side.DEFENSE, "변호인 카드", "내용 3", 5)
                ),
                List.of(
                        new CaseSnapshot.Penalty(1, "무죄 3"), new CaseSnapshot.Penalty(2, "무죄 2"),
                        new CaseSnapshot.Penalty(3, "무죄 1"), new CaseSnapshot.Penalty(4, "유죄 1"),
                        new CaseSnapshot.Penalty(5, "유죄 2"), new CaseSnapshot.Penalty(6, "유죄 3")
                )
        );
        return new TrialSession("trial-1", 4, 7, role, TrialMode.CHICKEN, snapshot, List.of(11, 12));
    }

    /* 판정 결과를 그대로 기록해 다음 차례로 넘긴다 */
    static void record(TrialSession session, JudgeResult result) {
        session.record("발언", result.issue(), result.evidence(), result.rebut(), result.novel(), result.weight());
    }

    /* seq 번째 발언 차례가 될 때까지 아무 발언으로 채운다 */
    static void moveTo(TrialSession session, int seq) {
        while (session.currentTurn().seq() < seq) {
            record(session, JudgeResult.of(session, true, true, true, true, 10));
        }
    }
}