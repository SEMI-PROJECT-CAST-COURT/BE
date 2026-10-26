package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.dto.response.SpeakingStageEventDTO;
import com.verdict.castcourt.domain.speaking.dto.response.SpeakingJudgeEventDTO;
import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.domain.speaking.dto.response.SpeakingTextEventDTO;
import com.verdict.castcourt.domain.speaking.dto.response.SpeakingTurnDoneEventDTO;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.dto.response.TrialStatusResponseDTO;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* 한 턴 처리 : 판정 → 상대 발언 → 판정 → 단계 */
class SpeakingServiceTurnTest {

    private final TrialSessionStore store = new TrialSessionStore();
    private final SpeakingService service = SpeakingTestFixture.service(
            store, SpeakingTestFixture.allYesJudge(), SpeakingTestFixture.fixedOpponent());

    @Test
    @DisplayName("상대 AI 허점은 판사가 모두 충족으로 봐도 미충족으로 기록되고, 사용자 발언은 그대로다")
    void opponentWeaknessesForced() {
        SpeakingService weakService = SpeakingTestFixture.service(store, SpeakingTestFixture.allYesJudge(),
                SpeakingTestFixture.opponent((session, onDelta) -> new com.verdict.castcourt.global.ai.AiResult<>(
                        SpeakingTestFixture.OPPONENT_TEXT, SpeakingTestFixture.USAGE),
                        java.util.EnumSet.of(com.verdict.castcourt.domain.trial.rule.DifficultyRule.Weakness.NO_EVIDENCE,
                                com.verdict.castcourt.domain.trial.rule.DifficultyRule.Weakness.REPEAT)));
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);

        weakService.runTurn(session, "내 모두 진술", new SpeakingTestFixture.Recorder());

        var mine = session.getSpeakings().get(0);
        var theirs = session.getSpeakings().get(1);
        assertEquals(com.verdict.castcourt.domain.speaking.entity.Grade.STRONG, mine.grade());
        assertEquals(com.verdict.castcourt.domain.speaking.entity.Grade.WEAK, theirs.grade());
        assertEquals(false, theirs.evidence());
        assertEquals(false, theirs.novel());
        assertEquals(true, theirs.issue());
    }

    @Test
    @DisplayName("검사 첫 턴 : judge → opponent_delta → opponent_judge → stage(공방 시작) → turn_done")
    void firstTurn() {
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        service.runTurn(session, "내 모두 진술", recorder);

        assertEquals(List.of("judge", "opponent_delta", "opponent_judge", "stage", "turn_done"), recorder.names());

        // 글자 조각을 이어 붙이면 상대 발언 전체
        String joined = recorder.events.stream()
                .filter(event -> event.name().equals("opponent_delta"))
                .map(event -> ((SpeakingTextEventDTO) event.data()).text())
                .reduce("", String::concat);
        assertEquals(SpeakingTestFixture.OPPONENT_TEXT, joined);

        // 다음 내 차례는 seq 3 = 공방 1번째 카드
        SpeakingStageEventDTO stage = (SpeakingStageEventDTO) recorder.events.get(recorder.events.size() - 2).data();
        assertEquals(Stage.CARD, stage.stage());
        assertEquals(1, stage.cardNo());
        assertEquals("현장 존재 여부", stage.topic());
        assertEquals(11, stage.card().cardId());

        assertEquals(2, session.getSpeakings().size());
        assertEquals(Speaker.AI, session.getSpeakings().get(1).speaker());
        assertEquals(SpeakingTurnDoneEventDTO.USER, ((SpeakingTurnDoneEventDTO) recorder.last()).nextTurn());
    }

    @Test
    @DisplayName("같은 카드 안에서는 stage 를 보내지 않는다")
    void sameCard() {
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.CHICKEN);
        service.runTurn(session, "모두 진술", new SpeakingTestFixture.Recorder());
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        service.runTurn(session, "카드 1 첫 공격", recorder);       // seq 3 → 다음 내 차례 seq 5 (같은 카드)

        assertEquals(List.of("judge", "opponent_delta", "opponent_judge", "turn_done"), recorder.names());
    }

    @Test
    @DisplayName("변호인 유저는 새 카드의 AI 검사 공격 전에 논점을 받는다 (도전자·철인)")
    void defenseTopicBeforeAttack() {
        for (TrialMode mode : List.of(TrialMode.CHALLENGER, TrialMode.IRON)) {
            TrialSession session = SpeakingTestFixture.session(Side.DEFENSE, mode);
            service.playAiOpening(session);
            SpeakingTestFixture.Recorder first = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "변호인 모두 진술", first);
            assertEquals(List.of("judge", "stage", "opponent_delta", "opponent_judge", "turn_done"), first.names());
            assertEquals("현장 존재 여부", ((SpeakingStageEventDTO) first.events.get(1).data()).topic());
            assertEquals("현장 존재 여부", session.currentTopic());
            service.runTurn(session, "첫 반론", new SpeakingTestFixture.Recorder());
            service.runTurn(session, "두 번째 반론", new SpeakingTestFixture.Recorder());
            SpeakingTestFixture.Recorder next = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "세 번째 반론", next);
            assertEquals(List.of("judge", "stage", "opponent_delta", "opponent_judge", "turn_done"), next.names());
            assertEquals("행동의 의도", ((SpeakingStageEventDTO) next.events.get(1).data()).topic());
        }
    }

    @Test
    @DisplayName("검사 유저는 두 모드에서 다음 공격 전에 현재 논점만 받고 상태 조회로 복원한다")
    void prosecutionTopicVisibility() {
        for (TrialMode mode : List.of(TrialMode.CHALLENGER, TrialMode.IRON)) {
            TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, mode);
            SpeakingTestFixture.Recorder first = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "모두 진술", first);
            assertEquals("현장 존재 여부", session.currentTopic());
            String json = JsonMapper.builder().build().writeValueAsString(TrialStatusResponseDTO.from(session));
            assertTrue(json.contains("현장 존재 여부"));
            assertFalse(json.contains("행동의 의도"));
            assertFalse(json.contains("카드 1"));
            service.runTurn(session, "첫 공격", new SpeakingTestFixture.Recorder());
            service.runTurn(session, "두 번째 공격", new SpeakingTestFixture.Recorder());
            SpeakingTestFixture.Recorder next = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "세 번째 공격", next);
            assertEquals("행동의 의도", session.currentTopic());
            assertEquals(1, next.events.stream().filter(e -> e.name().equals("stage")).count());
        }
    }

    @Test
    @DisplayName("새 논점 안내 뒤 AI가 실패해도 원본 재판의 논점과 차례는 유지된다")
    void failedTransitionKeepsOriginalTopic() {
        TrialSession session = SpeakingTestFixture.session(Side.DEFENSE, TrialMode.IRON);
        service.playAiOpening(session);
        service.runTurn(session, "모두 진술", new SpeakingTestFixture.Recorder());
        service.runTurn(session, "첫 반론", new SpeakingTestFixture.Recorder());
        service.runTurn(session, "두 번째 반론", new SpeakingTestFixture.Recorder());
        int seq = session.currentTurn().seq();
        SpeakingService failing = SpeakingTestFixture.service(store, SpeakingTestFixture.allYesJudge(),
                SpeakingTestFixture.opponent((draft, onDelta) -> { throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR); }));
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        failing.runTurn(session, "마지막 반론", recorder);

        assertEquals(List.of("judge", "stage", "error"), recorder.names());
        assertEquals("행동의 의도", ((SpeakingStageEventDTO) recorder.events.get(1).data()).topic());
        assertEquals(seq, session.currentTurn().seq());
        assertEquals("현장 존재 여부", session.currentTopic());
        assertEquals("현장 존재 여부", TrialStatusResponseDTO.from(session).topic());
    }

    @Test
    @DisplayName("겁쟁이 모드는 양측 모두 첫 공격 전 현재 카드만 받고 공방 전환 시 한 장씩 교체한다")
    void chickenCurrentCardBeforeAttack() {
        for (Side role : Side.values()) {
            TrialSession session = SpeakingTestFixture.session(role, TrialMode.CHICKEN);
            if (role == Side.DEFENSE) service.playAiOpening(session);
            SpeakingTestFixture.Recorder first = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "모두 진술", first);
            var firstStage = first.events.stream().filter(e -> e.name().equals("stage"))
                    .map(e -> (SpeakingStageEventDTO) e.data()).findFirst().orElseThrow();
            assertEquals(role == Side.PROSECUTION ? 11 : 21, firstStage.card().cardId());
            assertEquals(role, firstStage.card().side());
            if (role == Side.DEFENSE) assertEquals("stage", first.events.get(1).name());
            service.runTurn(session, "첫 공방", new SpeakingTestFixture.Recorder());
            service.runTurn(session, "두 번째 공방", new SpeakingTestFixture.Recorder());
            SpeakingTestFixture.Recorder next = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "세 번째 공방", next);
            var nextStage = next.events.stream().filter(e -> e.name().equals("stage"))
                    .map(e -> (SpeakingStageEventDTO) e.data()).findFirst().orElseThrow();
            assertEquals(role == Side.PROSECUTION ? 12 : 22, nextStage.card().cardId());
            assertEquals(role, nextStage.card().side());
            assertEquals(nextStage.card(), TrialStatusResponseDTO.from(session).card());
        }
    }

    @Test
    @DisplayName("검사로 끝까지 : 8턴 16발언, 마지막 turn_done 은 VERDICT")
    void playToEnd() {
        TrialSession session = SpeakingTestFixture.session(Side.PROSECUTION, TrialMode.IRON);
        SpeakingTestFixture.Recorder recorder = new SpeakingTestFixture.Recorder();

        while (!session.isFinished()) {
            recorder = new SpeakingTestFixture.Recorder();
            service.runTurn(session, "발언", recorder);
        }

        assertEquals(16, session.getSpeakings().size());
        assertEquals(50, session.getScore());        // 양쪽 모두 강함 판정이면 같은 카드 안에서 +w / -w 가 상쇄된다
        assertEquals(SpeakingTurnDoneEventDTO.VERDICT, ((SpeakingTurnDoneEventDTO) recorder.last()).nextTurn());
    }
}
