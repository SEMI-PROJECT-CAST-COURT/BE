package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialTurn;

/* 겁쟁이 모드에서 현재 공방 카드 한 장만 공개한다. 역할과 무관하게 같은 카드를 본다. */
public record TrialCardResponseDTO(Integer cardId, Side side, String title, String body, int weight) {
    public static TrialCardResponseDTO current(TrialSession session) {
        TrialTurn turn = session.currentTurn();
        if (!VisibilityRule.showsCurrentCard(session.getMode()) || turn == null || turn.cardId() == null) {
            return null;
        }
        return session.getSnapshot().cards().stream()
                .filter(card -> card.cardId().equals(turn.cardId()))
                .map(card -> new TrialCardResponseDTO(card.cardId(), card.side(), card.title(), card.body(), card.weight()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("스냅샷에 없는 현재 카드 : " + turn.cardId()));
    }
}
