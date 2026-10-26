package com.verdict.castcourt.domain.trial.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/* trial.case_snapshot (JSON 객체) <-> CaseSnapshot 변환기 */
@Converter
public class CaseSnapshotConverter implements AttributeConverter<CaseSnapshot, String> {

    // JSON 에 CaseSnapshot 에 없는 키가 있어도 오류 내지 않고 무시한다
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // 자바 -> DB
    @Override
    public String convertToDatabaseColumn(CaseSnapshot snapshot) {
        return snapshot == null ? null : MAPPER.writeValueAsString(snapshot);
    }

    // DB -> 자바
    @Override
    public CaseSnapshot convertToEntityAttribute(String json) {
        return json == null ? null : MAPPER.readValue(json, CaseSnapshot.class);
    }
}