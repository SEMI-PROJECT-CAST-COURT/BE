package com.verdict.castcourt.domain.speaking.dto.response;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSessionSpeaking;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.dto.response.TrialStatusResponseDTO;
import java.util.List;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* 이벤트 DTO : 실제로 나갈 JSON 모양을 확인한다 */
class SpeakingEventDTOTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // seq 1 검사 모두 진술 : ③ 제외(null) · 보통 · +6 · 게이지 56
    private final TrialSessionSpeaking speaking = new TrialSessionSpeaking(
            1, Stage.OPENING, Side.PROSECUTION, Speaker.USER, "발언",
            true, false, null, true, Grade.OK, 6, 56);

    @Test
    @DisplayName("CHICKEN 은 전부, ③ 제외는 \"rebut\":null 로 보인다")
    void chicken() {
        String json = jsonMapper.writeValueAsString(SpeakingJudgeEventDTO.of(speaking, TrialMode.CHICKEN));

        assertEquals("{\"checks\":{\"issue\":true,\"evidence\":false,\"rebut\":null,\"novel\":true},"
                + "\"grade\":\"OK\",\"delta\":6,\"scoreAfter\":56}", json);
    }

    @Test
    @DisplayName("CHALLENGER 는 delta · scoreAfter 만, IRON 은 빈 객체")
    void challengerAndIron() {
        assertEquals("{\"delta\":6,\"scoreAfter\":56}",
                jsonMapper.writeValueAsString(SpeakingJudgeEventDTO.of(speaking, TrialMode.CHALLENGER)));
        assertEquals("{}",
                jsonMapper.writeValueAsString(SpeakingJudgeEventDTO.of(speaking, TrialMode.IRON)));
    }

    @Test
    @DisplayName("stage : 공방이 아니면 cardNo · exchangeNo 가 빠진다")
    void stage() {
        TrialTurn closing = new TrialTurn(15, Stage.CLOSING, Side.PROSECUTION, Speaker.USER, null);
        TrialTurn card = new TrialTurn(9, Stage.CARD, Side.PROSECUTION, Speaker.USER, 12);

        assertEquals("{\"stage\":\"CLOSING\"}", jsonMapper.writeValueAsString(new SpeakingStageEventDTO(closing.stage(), null, null, null, null)));
        assertEquals("{\"stage\":\"CARD\",\"cardNo\":2,\"exchangeNo\":1}",
                jsonMapper.writeValueAsString(new SpeakingStageEventDTO(card.stage(), 2, 1, null, null)));
    }

    @Test
    @DisplayName("error : 설정 문제(503)만 다시 보내도 소용없다(retryable false)")
    void error() {
        SpeakingErrorEventDTO judgeFailed = SpeakingErrorEventDTO.of(ErrorCode.AI_JUDGE_FAILED, 1);
        SpeakingErrorEventDTO configMissing = SpeakingErrorEventDTO.of(ErrorCode.AI_CONFIGURATION_MISSING, 2);

        assertEquals(502, judgeFailed.status());
        assertEquals("제대로 답변을 생성하지 못했습니다.", judgeFailed.message());
        assertTrue(judgeFailed.retryable());
        assertEquals(503, configMissing.status());
        assertFalse(configMissing.retryable());
        assertEquals(2, configMissing.failCount());
    }
}
