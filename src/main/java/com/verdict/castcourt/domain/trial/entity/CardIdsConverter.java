package com.verdict.castcourt.domain.trial.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/* trial.card_ids (JSON 배열 [12, 9, 15]) <-> List<Integer> 변환기 */
@Converter
public class CardIdsConverter implements AttributeConverter<List<Integer>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    // 자바 -> DB : [12, 9, 15] 라는 JSON 문자열로 바꿔 저장한다
    @Override
    public String convertToDatabaseColumn(List<Integer> cardIds) {
        return cardIds == null ? null : MAPPER.writeValueAsString(cardIds);
    }

    // DB -> 자바 : JSON 문자열을 List<Integer> 로 읽는다
    @Override
    public List<Integer> convertToEntityAttribute(String json) {
        return json == null ? null : MAPPER.readValue(json, new TypeReference<List<Integer>>() {
        });
    }
}