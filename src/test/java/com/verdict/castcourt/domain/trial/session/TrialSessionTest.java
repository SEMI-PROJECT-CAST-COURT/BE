package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialSessionTest {

    private static final int WEIGHT = 10;

    @Test
    @DisplayName("검사로 한 판 : 검사만 강한 발언이면 게이지가 벌어져 카드 2장 · 16개로 끝난다")
    void prosecutionWins() {
        TrialSession session = newSession(Side.PROSECUTION, List.of(11, 22, 33));

        playToEnd(session, Side.PROSECUTION);

        assertTrue(session.isFinished());
        assertNull(session.currentTurn());
        assertEquals(16, session.getSpeakings().size());
        assertEquals(List.of(11, 22), session.usedCardIds());
        assertEquals(100, session.getScore());
    }

    @Test
    @DisplayName("변호인으로 한 판 : 첫 발언은 AI 검사, 내 차례는 짝수 seq")
    void defenseTurns() {
        TrialSession session = newSession(Side.DEFENSE, List.of(11, 22));

        assertEquals(Speaker.AI, session.currentTurn().speaker());
        playToEnd(session, Side.DEFENSE);

        session.getSpeakings().forEach(speaking ->
                assertEquals(speaking.seq() % 2 == 0 ? Speaker.USER : Speaker.AI, speaking.speaker()));
        assertEquals(0, session.getScore());
    }

    @Test
    @DisplayName("카드 2장 뒤 게이지가 47~53 이면 3번째 카드로 22개까지 간다")
    void thirdCard() {
        TrialSession session = newSession(Side.PROSECUTION, List.of(11, 22, 33));

        playToEnd(session, null);   // 모두 쟁점 이탈(OFF) → 게이지 50 유지

        assertEquals(22, session.getSpeakings().size());
        assertEquals(List.of(11, 22, 33), session.usedCardIds());
        assertEquals(Stage.CARD, session.getSpeakings().get(19).stage());       // seq 20 = 3번째 카드 마지막
        assertEquals(Stage.CLOSING, session.getSpeakings().get(20).stage());    // seq 21 = 최후 변론
    }

    @Test
    @DisplayName("카드를 2장만 뽑았으면 게이지가 팽팽해도 16개로 끝난다")
    void noThirdCardDrawn() {
        TrialSession session = newSession(Side.PROSECUTION, List.of(11, 22));

        playToEnd(session, null);

        assertEquals(16, session.getSpeakings().size());
    }

    @Test
    @DisplayName("끝난 재판에 발언을 더 넣으면 예외")
    void recordAfterFinish() {
        TrialSession session = newSession(Side.PROSECUTION, List.of(11, 22));
        playToEnd(session, Side.PROSECUTION);

        assertThrows(IllegalStateException.class,
                () -> session.record("더", true, true, true, true, false, WEIGHT));
    }

    @Test
    @DisplayName("발언마다 변화량 · 이후 게이지가 기록된다")
    void recordScore() {
        TrialSession session = newSession(Side.PROSECUTION, List.of(11, 22));

        TrialSessionSpeaking first = session.record("검사 모두 진술", true, true, null, true, false, WEIGHT);

        assertEquals(1, first.seq());
        assertEquals(10, first.delta());
        assertEquals(60, first.scoreAfter());
        assertEquals(60, session.getScore());
    }

    @Test
    @DisplayName("역할 이탈은 네 항목과 등급을 보존하고 정상 득점 대신 반대 방향 벌점을 기록한다")
    void roleViolationRecordedForBothSpeakers() {
        for (Side userRole : Side.values()) {
            TrialSession session = newSession(userRole, List.of(11, 22));
            TrialSessionSpeaking prosecutor = session.record("무죄 요청", true, true, null, true, true, 10);
            assertEquals(Grade.STRONG, prosecutor.grade());
            assertTrue(prosecutor.issue());
            assertTrue(prosecutor.roleViolation());
            assertEquals(-3, prosecutor.delta());
            assertEquals(47, session.getScore());
            TrialSession draft = session.copy();
            TrialSessionSpeaking defender = draft.record("엄벌 요청", true, true, null, true, true, 10);
            assertEquals(3, defender.delta());
            session.applyFrom(draft);
            assertEquals(50, session.getScore());
            assertTrue(session.getSpeakings().getLast().roleViolation());
            assertTrue(session.getSpeakings().getFirst().roleViolation());
        }
    }

    private TrialSession newSession(Side role, List<Integer> cardIds) {
        return new TrialSession("t-1", 4, 7, role, TrialMode.CHICKEN, null, cardIds);
    }

    // winner 쪽 발언은 STRONG, 반대쪽은 OFF · winner 가 null 이면 모두 OFF
    private void playToEnd(TrialSession session, Side winner) {
        while (!session.isFinished()) {
            TrialTurn turn = session.currentTurn();
            boolean strong = turn.side() == winner;
            Boolean rebut = turn.seq() == 1 ? null : strong;
            session.record("발언 " + turn.seq(), strong, strong, rebut, strong, false, WEIGHT);
        }
    }
}