package com.verdict.castcourt.domain.trial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionSpeaking;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.domain.trial.session.TrialTurnOrder;

import java.util.List;

/* 재판 상태 응답 (새로고침 때 화면 복원) · 모드에 따라 공개하지 않는 값은 JSON 에서 빠진다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrialStatusResponseDTO(
        Stage stage,                        // 지금 단계 (발언이 끝났으면 CLOSING)
        Integer cardNo,                     // 공방 카드 번호 1~3 (공방이 아니면 null)
        Integer exchangeNo,                 // 카드 안 공방 번호 1~3 (공방이 아니면 null)
        Integer score,                      // 게이지 (IRON 비공개)
        List<SpeakingResponse> speakings    // 지금까지의 발언
) {

    /* 발언 하나 · 내용은 항상, 판정 · 게이지는 모드에 따라 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpeakingResponse(
            int seq,
            Stage stage,
            Side side,
            Speaker speaker,
            String content,
            Grade grade,
            Boolean issue,
            Boolean evidence,
            Boolean rebut,
            Boolean novel,
            Integer delta,
            Integer scoreAfter
    ) {

        static SpeakingResponse of(TrialSessionSpeaking speaking, TrialMode mode) {

            boolean judgement = VisibilityRule.showsJudgement(mode);

            return new SpeakingResponse(
                    speaking.seq(),
                    speaking.stage(),
                    speaking.side(),
                    speaking.speaker(),
                    speaking.content(),
                    judgement ? speaking.grade() : null,
                    judgement ? speaking.issue() : null,
                    judgement ? speaking.evidence() : null,
                    judgement ? speaking.rebut() : null,
                    judgement ? speaking.novel() : null,
                    VisibilityRule.showsDelta(mode) ? speaking.delta() : null,
                    VisibilityRule.showsScore(mode) ? speaking.scoreAfter() : null
            );
        }
    }

    public static TrialStatusResponseDTO from(TrialSession session) {

        TrialMode mode = session.getMode();
        TrialTurn turn = session.currentTurn();     // 끝났으면 null

        List<SpeakingResponse> speakings = session.getSpeakings().stream()
                .map(speaking -> SpeakingResponse.of(speaking, mode))
                .toList();

        return new TrialStatusResponseDTO(
                turn == null ? Stage.CLOSING : turn.stage(),
                turn == null ? null : TrialTurnOrder.cardNo(turn),
                turn == null ? null : TrialTurnOrder.exchangeNo(turn),
                VisibilityRule.showsScore(mode) ? session.getScore() : null,
                speakings
        );
    }
}