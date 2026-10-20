package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/* SSE 를 열기 전 검사 */
class SpeakingServiceCheckTest {

    private final TrialSessionStore store = new TrialSessionStore();
    private final SpeakingService service = SpeakingTestFixture.service(
            store, SpeakingTestFixture.allYesJudge(), SpeakingTestFixture.fixedOpponent());

    @Test
    @DisplayName("내 차례 · 300자 이하면 통과하고 재판을 돌려준다")
    void ok() {
        TrialSession session = SpeakingTestFixture.stored(store, Side.PROSECUTION, TrialMode.CHICKEN);

        assertSame(session, service.checkSubmittable(4, "trial-1", "가".repeat(300)));
    }

    @Test
    @DisplayName("없는 재판 404, 남의 재판 403")
    void notFoundAndDenied() {
        SpeakingTestFixture.stored(store, Side.PROSECUTION, TrialMode.CHICKEN);

        assertError(ErrorCode.TRIAL_NOT_FOUND, () -> service.checkSubmittable(4, "없는-id", "발언"));
        assertError(ErrorCode.TRIAL_ACCESS_DENIED, () -> service.checkSubmittable(5, "trial-1", "발언"));
    }

    @Test
    @DisplayName("AI 차례(변호인의 첫 차례)면 400 SPEAKING_NOT_YOUR_TURN")
    void notYourTurn() {
        SpeakingTestFixture.stored(store, Side.DEFENSE, TrialMode.CHICKEN);     // seq 1 = AI 검사

        assertError(ErrorCode.SPEAKING_NOT_YOUR_TURN, () -> service.checkSubmittable(4, "trial-1", "발언"));
    }

    @Test
    @DisplayName("비었거나 공백뿐이거나 300자를 넘으면 400 SPEAKING_CONTENT_INVALID")
    void contentInvalid() {
        SpeakingTestFixture.stored(store, Side.PROSECUTION, TrialMode.CHICKEN);

        assertError(ErrorCode.SPEAKING_CONTENT_INVALID, () -> service.checkSubmittable(4, "trial-1", null));
        assertError(ErrorCode.SPEAKING_CONTENT_INVALID, () -> service.checkSubmittable(4, "trial-1", "   "));
        assertError(ErrorCode.SPEAKING_CONTENT_INVALID, () -> service.checkSubmittable(4, "trial-1", "가".repeat(301)));
    }

    private void assertError(ErrorCode expected, Executable executable) {
        BusinessException e = assertThrows(BusinessException.class, executable);
        assertEquals(expected, e.getErrorCode());
    }
}
