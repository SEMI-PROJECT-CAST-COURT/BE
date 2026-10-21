package com.verdict.castcourt.domain.penaltytier.dto;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record PenaltyDraftRequestDTO(
        int workId,
        String question,                // 쟁점
        String suspect,                 // 피의자
        String victim,                  // 피해자
        String summary,                 // 사건 개요
        String progress,                // 사건 경과
        List<String> excludeSentences   // 이전에 받은 처분 (없으면 빈 목록)
) {
    /* WebSocket 메시지(JSON) → 요청값 · 없는 필드는 빈 문자열 / 0 / 빈 목록이 되고, 검사는 서비스가 한다 */
    public static PenaltyDraftRequestDTO from(JsonNode message) {

        List<String> excludeSentences = new ArrayList<>();
        for (JsonNode sentence : message.path("excludeSentences")) {
            String text = sentence.asString("").strip();
            if (!text.isEmpty()) {
                excludeSentences.add(text);
            }
        }

        return new PenaltyDraftRequestDTO(
                message.path("workId").asInt(),
                text(message, "question"),
                text(message, "suspect"),
                text(message, "victim"),
                text(message, "summary"),
                text(message, "progress"),
                excludeSentences
        );
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").strip();
    }
}
