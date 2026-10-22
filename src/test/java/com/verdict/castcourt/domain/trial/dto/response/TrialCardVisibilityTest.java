package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.speaking.dto.response.SpeakingStageEventDTO;
import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrialCardVisibilityTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"PROSECUTION,CHICKEN", "DEFENSE,CHICKEN", "PROSECUTION,CHALLENGER",
            "DEFENSE,CHALLENGER", "PROSECUTION,IRON", "DEFENSE,IRON"})
    void stageCardOnlyAcrossAllStages(Side role, TrialMode mode) {
        TrialSession session = session(role, mode);
        TrialStartResponseDTO start = TrialStartResponseDTO.from(session);
        if (mode == TrialMode.CHICKEN) assertTrue(start.cards().isEmpty());
        else assertNull(start.cards());
        // 모두 진술은 첫 공방 카드, 최후 변론 · 종료 후는 마지막 공방 카드(13 : 대응 변호인 카드 없음)
        int[] sequences = {1, 3, 8, 9, 14, 15, 20, 21, 23};
        Integer[] expected = role == Side.PROSECUTION
                ? new Integer[]{11, 11, 11, 12, 12, 13, 13, 13, 13}
                : new Integer[]{22, 22, 22, 21, 21, null, null, null, null};
        for (int index = 0; index < sequences.length; index++) {
            while (!session.isFinished() && session.currentTurn().seq() < sequences[index]) {
                session.record("발언", false, false, false, false, 10);
            }
            TrialStatusResponseDTO status = TrialStatusResponseDTO.from(session);
            Integer expectedId = expected[index];
            if (mode == TrialMode.CHICKEN && expectedId != null) {
                assertEquals(expectedId, status.card().cardId());
                assertEquals(role, status.card().side());
                if (!session.isFinished()) assertEquals(status.card(), SpeakingStageEventDTO.of(session).card());
                String json = mapper.writeValueAsString(status);
                for (CaseSnapshot.Card card : session.getSnapshot().cards()) {
                    if (!card.cardId().equals(expectedId)) assertFalse(json.contains(card.body()));
                }
            } else {
                assertNull(status.card());
                assertFalse(mapper.writeValueAsString(status).contains("원문-"));
                if (!session.isFinished()) {
                    assertNull(SpeakingStageEventDTO.of(session).card());
                    assertFalse(mapper.writeValueAsString(SpeakingStageEventDTO.of(session)).contains("원문-"));
                }
            }
        }
    }

    @Test
    void defenseCardSurvivesCopyAndKeepsTheExistingRoundWeight() {
        TrialSession session = session(Side.DEFENSE, TrialMode.CHICKEN);
        while (session.currentTurn().seq() < 4) {
            session.record("발언", false, false, false, false, 10);
        }
        TrialCardResponseDTO card = TrialCardResponseDTO.current(session);
        assertEquals(22, card.cardId());
        assertEquals(7, card.weight());
        assertEquals(8, card.roundWeight());
        TrialSession draft = session.copy();
        assertEquals(card, TrialCardResponseDTO.current(draft));
        JudgeResult judged = JudgeResult.of(draft, true, true, true, true, 20);
        assertEquals(8, judged.weight());
        assertEquals(-8, draft.record("반론", judged.issue(), judged.evidence(), judged.rebut(), judged.novel(),
                judged.weight()).delta());
        assertEquals(50, session.getScore());
        assertEquals(card, TrialCardResponseDTO.current(session));
    }

    @Test
    void unusedThirdCardIsNeverPublished() {
        TrialSession session = session(Side.PROSECUTION, TrialMode.CHICKEN);
        while (session.currentTurn().seq() < 15) {
            boolean prosecutor = session.currentTurn().side() == Side.PROSECUTION;
            session.record("발언", prosecutor, prosecutor, prosecutor, prosecutor, 10);
        }
        assertEquals(List.of(11, 12), session.usedCardIds());
        // 최후 변론은 실제로 쓴 마지막 카드(12)를 보여 준다
        assertEquals(12, TrialStatusResponseDTO.from(session).card().cardId());
        assertFalse(mapper.writeValueAsString(TrialStatusResponseDTO.from(session)).contains("원문-13"));
    }

    private TrialSession session(Side role, TrialMode mode) {
        CaseSnapshot snapshot = new CaseSnapshot(7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "카드11", "원문-11", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "카드12", "원문-12", 12),
                        new CaseSnapshot.Card(13, Side.PROSECUTION, "카드13", "원문-13", 10),
                        new CaseSnapshot.Card(21, Side.DEFENSE, "변호인 카드21", "원문-21", 5),
                        new CaseSnapshot.Card(22, Side.DEFENSE, "변호인 카드22", "원문-22", 7),
                        new CaseSnapshot.Card(23, Side.DEFENSE, "미선정 변호인 카드", "원문-23", 9)), List.of());
        return new TrialSession("cards", 4, 7, role, mode, snapshot, List.of(11, 12, 13),
                Map.of(11, new TrialTopic("논점11", 22), 12, new TrialTopic("논점12", 21),
                        13, new TrialTopic("논점13", null)));
    }
}
