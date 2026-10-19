package com.verdict.castcourt.domain.trial.dto;

/* 재판 시작 요청 (POST /api/trials)
 *  role · mode 는 String 으로 받아 서비스에서 검사한다 (명세 오류 코드 TRIAL_ROLE_INVALID · TRIAL_MODE_INVALID 를 내려고)
 * */
public record TrialStartRequestDTO(
        Integer caseId,
        String role,        // PROSECUTION / DEFENSE
        String mode         // CHICKEN / CHALLENGER / IRON
) {
}