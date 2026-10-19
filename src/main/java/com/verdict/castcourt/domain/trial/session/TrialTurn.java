package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;

/* 발언 차례 한 칸 : 몇 번째(seq) 발언을 어느 단계에서 누가 하는지 */
public record TrialTurn(
        int seq,            // 1부터
        Stage stage,
        Side side,          // 발언하는 쪽
        Speaker speaker,    // 사용자 / AI
        Integer cardId      // 공방 단계의 카드 id (모두 진술 · 최후 변론은 null)
) {
}