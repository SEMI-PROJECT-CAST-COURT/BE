package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.speaking.dto.response.SpeakingStageEventDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
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
    void currentCardOnlyAcrossAllStages(Side role, TrialMode mode) {
        TrialSession session = session(role, mode);
        TrialStartResponseDTO start = TrialStartResponseDTO.from(session);
        if (mode == TrialMode.CHICKEN) assertTrue(start.cards().isEmpty());
        else assertNull(start.cards());
        assertNull(TrialStatusResponseDTO.from(session).card());
        int[] sequences = {3, 8, 9, 14, 15, 20, 21, 23};
        Integer[] expected = {11, 11, 12, 12, 13, 13, null, null};
        for (int index = 0; index < sequences.length; index++) {
            while (!session.isFinished() && session.currentTurn().seq() < sequences[index]) {
                session.record("발언", false, false, false, false, false, 10);
            }
            TrialStatusResponseDTO status = TrialStatusResponseDTO.from(session);
            if (mode == TrialMode.CHICKEN && expected[index] != null) {
                assertEquals(expected[index], status.card().cardId());
                assertEquals(Side.PROSECUTION, status.card().side());
                assertEquals(status.card(), SpeakingStageEventDTO.of(session).card());
                String json = mapper.writeValueAsString(status);
                assertFalse(json.contains("변호인 비공개 근거"));
                for (int id : List.of(11, 12, 13)) {
                    if (id != expected[index]) assertFalse(json.contains("원문-" + id));
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
    void unusedThirdCardIsNeverPublished() {
        TrialSession session = session(Side.PROSECUTION, TrialMode.CHICKEN);
        while (session.currentTurn().seq() < 15) {
            boolean prosecutor = session.currentTurn().side() == Side.PROSECUTION;
            session.record("발언", prosecutor, prosecutor, prosecutor, prosecutor, false, 10);
        }
        assertEquals(List.of(11, 12), session.usedCardIds());
        assertNull(TrialStatusResponseDTO.from(session).card());
        assertFalse(mapper.writeValueAsString(TrialStatusResponseDTO.from(session)).contains("원문-13"));
    }

    private TrialSession session(Side role, TrialMode mode) {
        CaseSnapshot snapshot = new CaseSnapshot(7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "카드11", "원문-11", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "카드12", "원문-12", 12),
                        new CaseSnapshot.Card(13, Side.PROSECUTION, "카드13", "원문-13", 10),
                        new CaseSnapshot.Card(21, Side.DEFENSE, "변호인 카드", "변호인 비공개 근거", 5)), List.of());
        return new TrialSession("cards", 4, 7, role, mode, snapshot, List.of(11, 12, 13),
                Map.of(11, "논점11", 12, "논점12", 13, "논점13"));
    }
}
