package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("마지막 활동 뒤 30분이 지난 판은 찾을 수 없고 보관소에서도 지워진다")
    void idleSessionRemoved() {
        TrialSession session = session("t-1", 4);
        store.save(session);
        Instant later = Instant.now().plus(TrialSessionStore.IDLE_TIMEOUT).plus(Duration.ofMinutes(1));

        assertTrue(store.findByTrialId("t-1", later).isEmpty());
        assertTrue(store.findByTrialId("t-1").isEmpty());      // 이미 지워져서 지금 찾아도 없다
    }

    @Test
    @DisplayName("활동하면 30분이 다시 시작된다")
    void touchKeepsSession() {
        TrialSession session = session("t-1", 4);
        store.save(session);
        Instant touchedAt = Instant.now().plus(Duration.ofMinutes(20));
        session.touch(touchedAt);

        assertFalse(store.findByTrialId("t-1", touchedAt.plus(Duration.ofMinutes(20))).isEmpty());
    }

    @Test
    @DisplayName("새 판을 넣을 때 다른 사용자의 버려진 판도 지운다")
    void saveRemovesOtherIdleSessions() {
        TrialSession idle = session("t-1", 4);
        store.save(idle);
        idle.touch(Instant.now().minus(TrialSessionStore.IDLE_TIMEOUT).minus(Duration.ofMinutes(1)));

        store.save(session("t-2", 5));

        assertTrue(store.findByUserId(4).isEmpty());
        assertEquals(5, store.findByTrialId("t-2").orElseThrow().getUserId());
    }

    private TrialSession session(String trialId, int userId) {
        return new TrialSession(trialId, userId, 7, Side.PROSECUTION, TrialMode.CHICKEN, null, List.of(11, 22));
    }
}