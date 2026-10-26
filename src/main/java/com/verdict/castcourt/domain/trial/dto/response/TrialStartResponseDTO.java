package com.verdict.castcourt.domain.trial.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.VisibilityRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;

import java.util.List;

/* 재판 시작 응답 · 모드에 따라 공개하지 않는 값은 null 이고 JSON 에서 빠진다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrialStartResponseDTO(
        String trialId,
        Stage stage,                    // 첫 단계 (OPENING)
        List<TrialCardResponseDTO> cards, // CHICKEN : 시작은 공방 전이므로 빈 목록 (기존 응답 키 유지)
        String progress,                // CHALLENGER : 사건 경과
        String openingSpeaking          // DEFENSE : AI 검사 모두 진술
) {

    public static TrialStartResponseDTO from(TrialSession session) {

        TrialMode mode = session.getMode();
        CaseSnapshot snapshot = session.getSnapshot();

        List<TrialCardResponseDTO> cards = VisibilityRule.showsCurrentCard(mode) ? List.of() : null;

        String progress = VisibilityRule.showsProgress(mode) ? snapshot.progress() : null;

        return new TrialStartResponseDTO(
                session.getTrialId(),
                session.currentTurn().stage(),
                cards,
                progress,
                openingSpeaking(session)
        );
    }

    // 변호인이면 시작 때 AI 가 한 첫 발언(검사 모두 진술) · 검사면 null
    private static String openingSpeaking(TrialSession session) {
        if (session.getRole() != Side.DEFENSE || session.getSpeakings().isEmpty()) {
            return null;
        }
        return session.getSpeakings().get(0).content();
    }
}