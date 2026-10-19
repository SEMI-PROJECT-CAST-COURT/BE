package com.verdict.castcourt.domain.workcase.dto.response;

public record DifficultyResponseDTO(
    Difficulty def,
    Difficulty pro
) {
    public static DifficultyResponseDTO of(int defSum, int proSum) {
        return new DifficultyResponseDTO(
                Difficulty.of(defSum, proSum),  // 변호사 난이도  
                Difficulty.of(proSum, defSum)   // 검사 난이도
        );
    }
}
