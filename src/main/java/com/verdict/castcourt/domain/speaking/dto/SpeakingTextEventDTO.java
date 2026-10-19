package com.verdict.castcourt.domain.speaking.dto;

/* SSE opponent_delta 이벤트 : AI 상대 발언의 글자 조각 { text }
 *  글자를 JSON 에 담아 보내면 조각 안에 줄바꿈이 있어도 SSE 모양이 깨지지 않는다.
 * */
public record SpeakingTextEventDTO(
        String text
) {
}