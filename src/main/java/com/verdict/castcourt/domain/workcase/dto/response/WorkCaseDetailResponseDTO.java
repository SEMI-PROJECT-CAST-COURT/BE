package com.verdict.castcourt.domain.workcase.dto.response;

import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;

import java.util.List;

public record WorkCaseDetailResponseDTO(
    long workId,
    String question,
    String suspect,
    String victim,
    String summary,
    String progress,
    List<CardResponse> cards,
    List<PenaltyResponse> penalties,
    DifficultyResponseDTO difficulty,
    WeightSumResponseDTO weightSum
) {
    public enum Side { DEFENSE, PROSECUTION }
    public record CardResponse(Side side, String title, String body, int weight) {}
    public record PenaltyResponse(int tier, String sentence) {}

    public static WorkCaseDetailResponseDTO of(WorkCaseEntity workCase,
                                               List<CardResponse> cards,
                                               List<PenaltyResponse> penalties) {
        WeightSumResponseDTO weightSum = WeightSumResponseDTO.from(cards);

        return new WorkCaseDetailResponseDTO(
                workCase.getWorkId(),
                workCase.getQuestion(), workCase.getSuspect(), workCase.getVictim(),
                workCase.getSummary(), workCase.getProgress(),
                cards, penalties,
                weightSum.toDifficulty(),
                weightSum
        );
    }
}
