package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.dto.SpeakingRequestDTO;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* 동시 제출 막기 · 브라우저 연결 끊김 */
class SpeakingServiceConcurrencyTest {

    private final TrialSessionStore store = new TrialSessionStore();

    @Test
    @DisplayName("처리 중 표시는 한 번에 하나만 얻는다")
    void tryStartTurn() {
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);

        assertTrue(session.tryStartTurn());
        assertFalse(session.tryStartTurn());
        session.endTurn();
        assertTrue(session.tryStartTurn());
    }

    @Test
    @DisplayName("앞 발언을 처리하는 중에 또 보내면 400 SPEAKING_NOT_YOUR_TURN")
    void secondSubmitWhileProcessing() {
        // 맡긴 작업을 실행하지 않는 실행기 → 첫 발언이 끝나지 않고 '처리 중'으로 남는다
        SpeakingService service = new SpeakingService(store, SpeakingTestFixture.allYesJudge(),
                SpeakingTestFixture.fixedOpponent(), task -> {
        });
        SpeakingTestFixture.stored(store, Side.PROSECUTION, TrialMode.CHICKEN);

        assertNotNull(service.submit(4, "trial-1", new SpeakingRequestDTO("첫 발언")));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.submit(4, "trial-1", new SpeakingRequestDTO("두 번째 발언")));
        assertEquals(ErrorCode.SPEAKING_NOT_YOUR_TURN, e.getErrorCode());
    }

    @Test
    @DisplayName("상대 발언 도중 브라우저가 끊기면 반영하지 않고, 실패로도 세지 않고, 처리 중을 푼다")
    void clientClosed() {
        SpeakingService service = SpeakingTestFixture.service(
                store, SpeakingTestFixture.allYesJudge(), SpeakingTestFixture.fixedOpponent());
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);
        session.tryStartTurn();

        // 글자 조각을 보내려는 순간 끊긴 것처럼
        service.runTurn(session, "발언", (event, data) -> {
            if (event.equals("opponent_delta")) {
                throw new SpeakingService.ClientClosedException();
            }
        });

        assertEquals(0, session.getSpeakings().size());
        assertEquals(1, session.increaseFailCount());   // 끊김은 실패로 세지 않았다
        assertTrue(session.tryStartTurn());             // 처리 중이 풀렸다
    }
}
