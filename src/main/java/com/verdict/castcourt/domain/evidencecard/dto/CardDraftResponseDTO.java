package com.verdict.castcourt.domain.evidencecard.dto;

/* AI 근거 카드 초안 1장 (done 의 json)
 *  사건 등록(POST /api/admin/cases)의 cards 한 장과 같은 모양이라, 관리자가 고친 뒤 목록에 넣어 그대로 보내면 된다.
 *  아직 저장 전이라 id 는 없다.
 * */
public record CardDraftResponseDTO(
        String side,        // PROSECUTION / DEFENSE (요청한 진영 그대로)
        String title,       // 20자 이내
        String body,        // 70자 이내
        int weight          // 1 ~ 20
) {
}
