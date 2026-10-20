package com.verdict.castcourt.domain.speaking.dto.request;

/* 발언 제출 요청 { content } (1~300자 · 검사는 SpeakingService 에서 한다) */
public record SpeakingRequestDTO(
        String content
) {
}