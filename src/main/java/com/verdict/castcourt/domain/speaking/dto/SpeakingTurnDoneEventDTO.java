package com.verdict.castcourt.domain.speaking.dto;

/* SSE turn_done 이벤트 : 이번 발언 처리가 끝났고 결과가 재판에 반영됐다 { nextTurn }
 *  nextTurn = USER (다시 내 차례) / VERDICT (발언이 모두 끝나 판결 받기 가능)
 * */
public record SpeakingTurnDoneEventDTO(
        String nextTurn
) {

    public static final String USER = "USER";
    public static final String VERDICT = "VERDICT";
}