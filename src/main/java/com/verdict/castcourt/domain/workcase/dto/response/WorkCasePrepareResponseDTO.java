package com.verdict.castcourt.domain.workcase.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;

import java.util.List;

/* 재판 준비 DTO */
public record WorkCasePrepareResponseDTO(
    @JsonProperty("case") CaseInfoResponse caseInfo,    // 사건 정보
    RolesResponse roles,                                // 역할별 완료 난이도, 완료 여부
    List<PenaltyTierDTO> penalties                     // 사건의 형량 목록
) {
    // 사건 정보
    public record CaseInfoResponse(String question, String suspect, String victim, String summary) {}

    // 역할별 정보
    public record RolesResponse(RoleResponse def, RoleResponse pro) {}
    public record RoleResponse(Difficulty difficulty, boolean recorded) {}

    public static WorkCasePrepareResponseDTO of(WorkCaseEntity workCase,
                                                DifficultyResponseDTO difficulty,
                                                boolean defRecorded,
                                                boolean proRecorded,
                                                List<PenaltyTierDTO> penalties) {
        return new WorkCasePrepareResponseDTO(
                new CaseInfoResponse(
                        workCase.getQuestion(),
                        workCase.getSuspect(),
                        workCase.getVictim(),
                        workCase.getSummary()
                ),
                new RolesResponse(
                        new RoleResponse(difficulty.def(), defRecorded),
                        new RoleResponse(difficulty.pro(), proRecorded)
                ),
                penalties
        );
    }
}
