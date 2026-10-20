package com.verdict.castcourt.domain.trial.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/* 진행 중 재판 보관소 (서버 메모리)
 *  사용자당 1판 : 새로 시작하면 이전 판을 덮어쓴다.
 *  서버를 다시 켜면 사라진다. (기획서 확정 사항)
 * */
@Component
public class TrialSessionStore {

    // key = userId · 여러 요청이 동시에 읽고 써도 안전한 Map
    private final Map<Integer, TrialSession> sessions = new ConcurrentHashMap<>();

    /* 저장 · 같은 사용자의 이전 판은 덮어쓴다 */
    public void save(TrialSession session) {
        sessions.put(session.getUserId(), session);
    }

    public Optional<TrialSession> findByUserId(int userId) {
        return Optional.ofNullable(sessions.get(userId));
    }

    /* 재판 id 로 찾기 (재판 상태 조회)
     *  진행 중 재판은 사용자 수만큼만 있어서 전부 훑어도 충분하다
     * */
    public Optional<TrialSession> findByTrialId(String trialId) {
        return sessions.values().stream()
                .filter(session -> session.getTrialId().equals(trialId))
                .findFirst();
    }

    /* 이 판이 아직 보관 중일 때만 지운다 (그사이 새로 시작한 판은 지우지 않음) */
    public void remove(TrialSession session) {
        sessions.remove(session.getUserId(), session);
    }
}