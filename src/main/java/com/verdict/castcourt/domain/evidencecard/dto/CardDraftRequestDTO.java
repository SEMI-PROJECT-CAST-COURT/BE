package com.verdict.castcourt.domain.evidencecard.dto;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record CardDraftRequestDTO(
        int workId,
        String side,            // CARDS : 만들 카드의 진영 (PROSECUTION / DEFENSE)
        String question,        // 쟁점
        String suspect,         // 피의자
        String victim,          // 피해자
        String summary,         // 사건 개요
        String progress,        // 사건 경과
        List<Card> cards,       // 화면의 다른 카드 (양쪽 다 보내도 됨 · 없으면 빈 목록)
        Card card               // CARD_SUGGEST : 검토할 카드 (없으면 null)
) {
    /* 카드 한 장 (cards 의 카드는 weight 를 보내지 않아도 된다 → 0) */
    public record Card(String side, String title, String body, int weight) {
        /* 진영 · 제목 · 내용이 같은 카드인지 (나머지 카드에 검토할 카드가 섞여 와도 걸러 내려고) */
        public boolean sameContent(Card other) {
            return other != null
                    && side.equals(other.side)
                    && title.equals(other.title)
                    && body.equals(other.body);
        }
    }

    /* WebSocket 메시지(JSON) → 요청값 · 없는 필드는 빈 문자열 / 0 / 빈 목록 / null 이 되고, 검사는 서비스가 한다 */
    public static CardDraftRequestDTO from(JsonNode message) {
        List<Card> cards = new ArrayList<>();
        for (JsonNode card : message.path("cards")) {
            cards.add(card(card));
        }

        JsonNode target = message.path("card");

        return new CardDraftRequestDTO(
                message.path("workId").asInt(),
                text(message, "side"),
                text(message, "question"),
                text(message, "suspect"),
                text(message, "victim"),
                text(message, "summary"),
                text(message, "progress"),
                cards,
                target.isObject() ? card(target) : null
        );
    }

    private static Card card(JsonNode node) {
        return new Card(text(node, "side"), text(node, "title"), text(node, "body"), node.path("weight").asInt());
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").strip();
    }
}
