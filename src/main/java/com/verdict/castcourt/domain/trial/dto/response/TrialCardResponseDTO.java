package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;

/* 겁쟁이 모드에서 현재 논점에 대응하는 사용자 측 카드 한 장만 공개한다. */
public record TrialCardResponseDTO(Integer cardId, Side side, String title, String body, int weight, int roundWeight) {
    public static TrialCardResponseDTO current(TrialSession session) {
        if (!VisibilityRule.showsCurrentCard(session.getMode())) {
            return null;
        }
        CaseSnapshot.Card card = session.currentCard(session.getRole());
        if (card == null) {
            return null;
        }
        return new TrialCardResponseDTO(card.cardId(), card.side(), card.title(), card.body(), card.weight(),
                session.currentCard(Side.PROSECUTION).weight());
    }
}
