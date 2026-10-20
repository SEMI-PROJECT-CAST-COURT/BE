package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.ai.model.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/* 발언 제출 테스트가 함께 쓰는 재료
 *  - JudgeAi · OpponentAi 는 메서드가 하나뿐인 인터페이스라 람다로 바로 만들 수 있다
 *  - Recorder : SSE 대신 보낸 이벤트를 목록에 모아 두는 sender
 * */
final class SpeakingTestFixture {

    static final String OPPONENT_TEXT = "재판장님, 상대 측입니다.";
    static final AiUsage USAGE = new AiUsage("test-model", 0, 0, LocalDateTime.now(), 0);

    private SpeakingTestFixture() {
    }

    /* 카드 2장(11 · 12)으로 진행하는 재판 · userId 4 */
    static TrialSession session(Side role, TrialMode mode) {
        CaseSnapshot snapshot = new CaseSnapshot(
                7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "카드 1", "내용", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "카드 2", "내용", 12)),
                List.of());
        return new TrialSession("trial-1", 4, 7, role, mode, snapshot, List.of(11, 12));
    }

    /* 보관소에 넣은 재판 */
    static TrialSession stored(TrialSessionStore store, Side role, TrialMode mode) {
        TrialSession session = session(role, mode);
        store.save(session);
        return session;
    }

    /* 언제나 ①~④ 모두 충족으로 판정하는 판사 */
    static JudgeAi allYesJudge() {
        return (session, content) -> new AiResult<>(
                JudgeResult.of(session, true, true, true, true, 10), USAGE);
    }

    /* 정해진 문장을 한 글자씩 넘기는 상대 */
    static OpponentAi fixedOpponent() {
        return (session, onDelta) -> {
            OPPONENT_TEXT.codePoints().forEach(c -> onDelta.accept(Character.toString(c)));
            return new AiResult<>(OPPONENT_TEXT, USAGE);
        };
    }

    /* 작업 실행기 대신 Runnable::run → 맡긴 작업을 그 자리에서 바로 실행한다 (테스트가 기다릴 필요 없음) */
    static SpeakingService service(TrialSessionStore store, JudgeAi judgeAi, OpponentAi opponentAi) {
        return new SpeakingService(store, judgeAi, opponentAi, Runnable::run);
    }

    record Event(String name, Object data) {
    }

    static class Recorder implements BiConsumer<String, Object> {

        final List<Event> events = new ArrayList<>();

        @Override
        public void accept(String name, Object data) {
            events.add(new Event(name, data));
        }

        /* 이벤트 이름만 순서대로 (opponent_delta 는 여러 번이라 하나로 줄인다) */
        List<String> names() {
            List<String> names = new ArrayList<>();
            for (Event event : events) {
                if (names.isEmpty() || !names.get(names.size() - 1).equals(event.name())) {
                    names.add(event.name());
                }
            }
            return names;
        }

        Object last() {
            return events.get(events.size() - 1).data();
        }
    }
}
