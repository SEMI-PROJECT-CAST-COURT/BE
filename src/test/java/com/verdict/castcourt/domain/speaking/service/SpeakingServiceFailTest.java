package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.ai.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.OpponentAi;
import com.verdict.castcourt.domain.speaking.dto.SpeakingErrorEventDTO;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/* 실패 처리 : 실패하면 error 이벤트 하나로 끝나고, 발언 · 게이지는 그대로 */
class SpeakingServiceFailTest {

    private final TrialSessionStore store = new TrialSessionStore();

    @Test
    @DisplayName("판정 실패 : AI_UPSTREAM_ERROR → AI_JUDGE_FAILED, 아무것도 반영되지 않는다")
    void judgeFails() {
        JudgeAi failingJudge = (session, content) -> {
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        };
        SpeakingService service = SpeakingTestFixture.service(store, failingJudge, SpeakingTestFixture.fixedOpponent());
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        service.runTurn(session, "발언", recorder);

        assertEquals(List.of("error"), recorder.names());
        SpeakingErrorEventDTO error = (SpeakingErrorEventDTO) recorder.last();
        assertEquals("AI_JUDGE_FAILED", error.code());
        assertEquals(1, error.failCount());
        assertEquals(0, session.getSpeakings().size());
        assertEquals(50, session.getScore());
    }

    @Test
    @DisplayName("상대 발언 실패 : judge 는 이미 나갔어도 내 발언까지 반영하지 않는다 (같은 발언을 다시 보낸다)")
    void opponentFails() {
        OpponentAi failingOpponent = (session, onDelta) -> {
            onDelta.accept("재판장님,");
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        };
        SpeakingService service = SpeakingTestFixture.service(store, SpeakingTestFixture.allYesJudge(), failingOpponent);
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        service.runTurn(session, "발언", recorder);

        assertEquals(List.of("judge", "opponent_delta", "error"), recorder.names());
        assertEquals("AI_OPPONENT_FAILED", ((SpeakingErrorEventDTO) recorder.last()).code());
        assertEquals(0, session.getSpeakings().size());
        assertEquals(50, session.getScore());
    }

    @Test
    @DisplayName("failCount 는 연속 실패만 센다 : 실패 2번 → 성공 → 다시 1부터")
    void failCount() {
        AtomicInteger calls = new AtomicInteger();
        // 1 · 2번째 판정은 실패, 그 뒤로는 성공
        JudgeAi flakyJudge = (session, content) -> {
            if (calls.incrementAndGet() <= 2) {
                throw new BusinessException(ErrorCode.AI_TIMEOUT);
            }
            return SpeakingTestFixture.allYesJudge().judge(session, content);
        };
        SpeakingService service = SpeakingTestFixture.service(store, flakyJudge, SpeakingTestFixture.fixedOpponent());
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);

        assertEquals(1, failCountOf(service, session));
        assertEquals(2, failCountOf(service, session));

        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();
        service.runTurn(session, "발언", recorder);
        assertEquals("turn_done", recorder.names().get(recorder.names().size() - 1));

        assertEquals(1, session.increaseFailCount());   // 성공하면서 0 으로 돌아갔다
    }

    @Test
    @DisplayName("설정 문제(503)는 retryable false, 예상 못 한 오류는 500")
    void otherErrors() {
        JudgeAi noKey = (session, content) -> {
            throw new BusinessException(ErrorCode.AI_CONFIGURATION_MISSING);
        };
        JudgeAi broken = (session, content) -> {
            throw new IllegalStateException("버그");
        };
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);

        SpeakingTestFixture.Recorder first = new SpeakingTestFixture.Recorder();
        SpeakingTestFixture.service(store, noKey, SpeakingTestFixture.fixedOpponent()).runTurn(session, "발언", first);
        SpeakingErrorEventDTO configMissing = (SpeakingErrorEventDTO) first.last();
        assertEquals(503, configMissing.status());
        assertFalse(configMissing.retryable());

        SpeakingTestFixture.Recorder second = new SpeakingTestFixture.Recorder();
        SpeakingTestFixture.service(store, broken, SpeakingTestFixture.fixedOpponent()).runTurn(session, "발언", second);
        assertEquals("INTERNAL_SERVER_ERROR", ((SpeakingErrorEventDTO) second.last()).code());
    }

    private int failCountOf(SpeakingService service, TrialSession session) {
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();
        service.runTurn(session, "발언", recorder);
        return ((SpeakingErrorEventDTO) recorder.last()).failCount();
    }
}
