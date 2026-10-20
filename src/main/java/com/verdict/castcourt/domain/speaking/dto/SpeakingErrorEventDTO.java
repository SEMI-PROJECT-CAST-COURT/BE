package com.verdict.castcourt.domain.speaking.dto;

import com.verdict.castcourt.global.exception.ErrorCode;

/* SSE error 이벤트 : 스트림 도중 실패 { status, code, message, retryable, failCount }
 *  SSE 가 열린 뒤에는 HTTP 상태 코드를 바꿀 수 없어서, 상태 코드도 data 안에 넣는다. (명세 v6 신규 필드 status · code)
 *  이 이벤트로 스트림이 끝나고, 실패한 발언은 반영하지 않는다. 화면은 같은 발언을 다시 보내면 된다.
 * */
public record SpeakingErrorEventDTO(
        int status,
        String code,
        String message,
        boolean retryable,      // 다시 보내면 될 수도 있는지 (설정 문제 503 만 false)
        int failCount           // 연속 실패 횟수 · 3이면 화면이 나가기를 권한다 (명세 v6)
) {

    public static SpeakingErrorEventDTO of(ErrorCode errorCode, int failCount) {
        return new SpeakingErrorEventDTO(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode != ErrorCode.AI_CONFIGURATION_MISSING,
                failCount
        );
    }
}