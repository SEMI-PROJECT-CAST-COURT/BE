package com.verdict.castcourt.domain.trial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;

import java.util.List;

/* 재판 시작 응답 · 모드에 따라 공개하지 않는 값은 null 이고 JSON 에서 빠진다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrialStartResponseDTO(
        String trialId,
        Stage stage,                    // 첫 단계 (OPENING)
        List<CardResponse> cards,       // CHICKEN : 내 쪽 근거 카드
        String progress,                // CHALLENGER : 사건 경과
        String openingSpeaking          // DEFENSE : AI 검사 모두 진술 (6장에서 채운다)
) {

    /* 근거 카드 한 장 */
    public record CardResponse(Integer cardId, String title, String body, int weight) {
    }

    public static TrialStartResponseDTO from(TrialSession session) {

        TrialMode mode = session.getMode();
        CaseSnapshot snapshot = session.getSnapshot();

        List<CardResponse> cards = VisibilityRule.showsMyCards(mode)
                ? snapshot.cards().stream()
                .filter(card -> card.side() == session.getRole())
                .map(card -> new CardResponse(card.cardId(), card.title(), card.body(), card.weight()))
                .toList()
                : null;

        String progress = VisibilityRule.showsProgress(mode) ? snapshot.progress() : null;

        return new TrialStartResponseDTO(
                session.getTrialId(),
                session.currentTurn().stage(),
                cards,
                progress,
                null
        );
    }
}