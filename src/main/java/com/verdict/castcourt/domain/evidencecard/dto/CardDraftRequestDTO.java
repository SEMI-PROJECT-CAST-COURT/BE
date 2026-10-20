package com.verdict.castcourt.domain.evidencecard.dto;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/* AI 근거 카드 초안 요청 (AI 사건 초안 · step = CARDS) : 한 번에 1장
 *  {
 *    "type": "draft", "step": "CARDS", "workId": 1,
 *    "side": "PROSECUTION",
 *    "question": "...", "suspect": "...", "victim": "...", "summary": "...", "progress": "...",
 *    "cards": [ { "side": "DEFENSE", "title": "...", "body": "..." } ]    ← 이미 만든 카드 (없으면 생략)
 *  }
 *  - side  : 이번에 만들 카드의 진영
 *  - cards : 지금까지 만든 카드 (양쪽 모두). 같은 내용이 또 나오지 않게 AI 에 함께 보낸다.
 *  사건 필드 이름은 사건 등록(WorkCaseRequestDTO)과 맞췄다. (명세에 CARDS 요청 상세가 없어 팀 확인 필요)
 * */
public record CardDraftRequestDTO(
        int workId,
        String side,            // PROSECUTION / DEFENSE
        String question,        // 쟁점
        String suspect,         // 피의자
        String victim,          // 피해자
        String summary,         // 사건 개요
        String progress,        // 사건 경과
        List<Card> cards        // 이미 만든 카드 (없으면 빈 목록)
) {

    /* 이미 만든 카드 한 장 (겹침 방지용이라 가중치는 받지 않는다) */
    public record Card(String side, String title, String body) {
    }

    /* WebSocket 메시지(JSON) → 요청값 · 없는 필드는 빈 문자열 / 0 / 빈 목록이 되고, 검사는 서비스가 한다 */
    public static CardDraftRequestDTO from(JsonNode message) {

        List<Card> cards = new ArrayList<>();
        for (JsonNode card : message.path("cards")) {
            cards.add(new Card(text(card, "side"), text(card, "title"), text(card, "body")));
        }

        return new CardDraftRequestDTO(
                message.path("workId").asInt(),
                text(message, "side"),
                text(message, "question"),
                text(message, "suspect"),
                text(message, "victim"),
                text(message, "summary"),
                text(message, "progress"),
                cards
        );
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").strip();
    }
}
