package com.verdict.castcourt.domain.evidencecard.dto;

public record CardSuggestResponseDTO(
        String side,        // PROSECUTION / DEFENSE (고칠 카드 그대로)
        String message,     // 제안 문구 한 줄
        String title,       // 고친 제목 (20자 이내)
        String body,        // 고친 내용 (100자 이내)
        int weight,         // 추천 가중치 (1 ~ 20)
        boolean changed     // 원래 카드와 다른가 (서버가 비교해서 채운다)
) {
}
