package com.verdict.castcourt.domain.trial.entity;

import java.util.List;

/* 사건 스냅샷 (trial.case_snapshot)
 *  재판을 시작한 순간의 사건 · 카드 · 형벌표를 통째로 복사해 둔 것.
 *  관리자가 나중에 사건을 고치거나 지워도, 끝난 재판 기록은 이 복사본으로 계속 보여 준다.
 *  JSON 키 이름은 더미 데이터(INSERT_DATA.sql)의 case_snapshot 과 같다.
 * */
public record CaseSnapshot(
        Integer caseId,
        Integer workId,
        String workTitle,
        String question,        // 쟁점
        String suspect,         // 피의자
        String victim,          // 피해자
        String summary,         // 사건 개요
        String progress,        // 사건 경과
        List<Card> cards,       // 근거 카드 (양측 전부)
        List<Penalty> penalties // 형벌표 6단계
) {

    /* 형벌표에서 그 단계의 처분 문장 · 형벌표에 그 단계가 없으면 null (판결 받기 · 재판 기록 응답) */
    public String sentenceOf(int tier) {
        return penalties.stream()
                .filter(penalty -> penalty.tier() == tier)
                .map(Penalty::sentence)
                .findFirst()
                .orElse(null);
    }

    /* 근거 카드 한 장 */
    public record Card(
            Integer cardId,
            Side side,
            String title,
            String body,
            int weight          // 가중치 1~20
    ) {
    }

    /* 형벌표 한 단계 */
    public record Penalty(
            int tier,           // 1~6
            String sentence     // 처분 문장
    ) {
    }
}