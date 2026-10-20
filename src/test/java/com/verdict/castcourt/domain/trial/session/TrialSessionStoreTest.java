package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialSessionStoreTest {

    private final TrialSessionStore store = new TrialSessionStore();

    @Test
    @DisplayName("같은 사용자가 새로 시작하면 이전 판을 덮어쓴다")
    void overwrite() {
        TrialSession first = session("t-1", 4);
        TrialSession second = session("t-2", 4);

        store.save(first);
        store.save(second);

        assertSame(second, store.findByUserId(4).orElseThrow());
        assertTrue(store.findByTrialId("t-1").isEmpty());
    }

    @Test
    @DisplayName("재판 id 로 찾을 수 있다")
    void findByTrialId() {
        store.save(session("t-1", 4));
        store.save(session("t-2", 5));

        assertEquals(5, store.findByTrialId("t-2").orElseThrow().getUserId());
    }

    @Test
    @DisplayName("이전 판을 지우려 해도 새로 시작한 판은 남는다")
    void removeOnlySameSession() {
        TrialSession first = session("t-1", 4);
        TrialSession second = session("t-2", 4);
        store.save(first);
        store.save(second);

        store.remove(first);
        assertSame(second, store.findByUserId(4).orElseThrow());

        store.remove(second);
        assertTrue(store.findByUserId(4).isEmpty());
    }

    private TrialSession session(String trialId, int userId) {
        return new TrialSession(trialId, userId, 7, Side.PROSECUTION, TrialMode.CHICKEN, null, List.of(11, 22));
    }
}