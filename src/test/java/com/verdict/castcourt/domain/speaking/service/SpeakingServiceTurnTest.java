package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.dto.SpeakingStageEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingTextEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingTurnDoneEventDTO;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/* 한 턴 처리 : 판정 → 상대 발언 → 판정 → 단계 */
class SpeakingServiceTurnTest {

    private final TrialSessionStore store = new TrialSessionStore();
    private final SpeakingService service = SpeakingTestFixture.service(
            store, SpeakingTestFixture.allYesJudge(), SpeakingTestFixture.fixedOpponent());

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
