package com.verdict.castcourt.domain.penaltytier.dto;

/* AI 형벌표 한 단계 (done 의 json = 이 목록 6개, tier 1 → 6 순서)
 *  사건 등록(POST /api/admin/cases)의 penalties 한 줄 { tier, sentence } 에 화면 표시용 title 을 더한 모양이다.
 *  관리자가 고친 뒤 tier · sentence 만 사건 등록으로 보내면 된다.
 * */
public record PenaltyDraftResponseDTO(
        int tier,           // 1 ~ 6 (재판의 penaltyTier 와 같은 값)
        String title,       // 무죄 3단계 · 무죄 2단계 · 무죄 1단계 · 유죄 1단계 · 유죄 2단계 · 유죄 3단계
        String sentence     // 처분 (50자 이내)
) {
}
