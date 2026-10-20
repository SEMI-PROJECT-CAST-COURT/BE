package com.verdict.castcourt.domain.trial.session;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/* 진행 중 재판 보관소 (서버 메모리)
 *  사용자당 1판 : 새로 시작하면 이전 판을 덮어쓴다.
 *  서버를 다시 켜면 사라진다. (기획서 확정 사항)
 *  마지막 활동 뒤 30분이 지난 판은 버려진 판으로 보고, 찾을 때 · 새 판을 넣을 때 지운다. (스케줄러 없이)
 * */
@Component
public class TrialSessionStore {

    // 이 시간 동안 활동(재판 시작 · 발언 제출)이 없으면 버려진 판
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    // key = userId · 여러 요청이 동시에 읽고 써도 안전한 Map
    private final Map<Integer, TrialSession> sessions = new ConcurrentHashMap<>();

    /* 저장 · 같은 사용자의 이전 판은 덮어쓴다
     *  넣기 전에 다른 사용자의 버려진 판도 함께 정리한다 (아무도 다시 찾지 않는 판이 계속 남지 않게)
     * */
    public void save(TrialSession session) {
        removeIdle(Instant.now());
        sessions.put(session.getUserId(), session);
    }

    public Optional<TrialSession> findByUserId(int userId) {
        return Optional.ofNullable(sessions.get(userId))
                .filter(session -> isAlive(session, Instant.now()));
    }

    /* 재판 id 로 찾기 (재판 상태 조회 · 발언 제출 · 판결 · 나가기)
     *  진행 중 재판은 사용자 수만큼만 있어서 전부 훑어도 충분하다
     * */
    public Optional<TrialSession> findByTrialId(String trialId) {
        return findByTrialId(trialId, Instant.now());
    }

    // 시각을 받는 버전 (테스트에서 30분 뒤를 흉내 낼 때 쓴다)
    Optional<TrialSession> findByTrialId(String trialId, Instant now) {
        return sessions.values().stream()
                .filter(session -> session.getTrialId().equals(trialId))
                .findFirst()
                .filter(session -> isAlive(session, now));
    }

    /* 이 판이 아직 보관 중일 때만 지운다 (그사이 새로 시작한 판은 지우지 않음) */
    public void remove(TrialSession session) {
        sessions.remove(session.getUserId(), session);
    }

    // 버려진 판 전부 지우기
    void removeIdle(Instant now) {
        sessions.values().removeIf(session -> session.isIdle(now, IDLE_TIMEOUT));
    }

    // 살아 있는 판인지 · 버려진 판이면 여기서 지우고 false (없는 판처럼 404 가 된다)
    private boolean isAlive(TrialSession session, Instant now) {
        if (session.isIdle(now, IDLE_TIMEOUT)) {
            remove(session);
            return false;
        }
        return true;
    }
}