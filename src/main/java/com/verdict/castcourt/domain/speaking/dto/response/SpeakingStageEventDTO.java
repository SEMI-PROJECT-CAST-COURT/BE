package com.verdict.castcourt.domain.speaking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.domain.trial.session.TrialTurnOrder;

/* SSE stage 이벤트 : 단계나 카드가 바뀌었을 때만 보낸다 { stage, cardNo, exchangeNo }
 *  카드 2장 뒤 게이지가 47~53 이면 여기서 cardNo 3 이 처음 보인다.
 * */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakingStageEventDTO(
        Stage stage,
        Integer cardNo,         // 공방이 아니면 빠진다
        Integer exchangeNo
) {

    /* 다음 차례(turn) 기준 단계 */
    public static SpeakingStageEventDTO of(TrialTurn turn) {
        return new SpeakingStageEventDTO(turn.stage(), TrialTurnOrder.cardNo(turn), TrialTurnOrder.exchangeNo(turn));
    }
}