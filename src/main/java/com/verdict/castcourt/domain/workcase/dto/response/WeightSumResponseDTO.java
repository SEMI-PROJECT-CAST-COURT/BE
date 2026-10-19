package com.verdict.castcourt.domain.workcase.dto.response;

import java.util.List;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseDetailResponseDTO.*;

/* 각 역할별 근거 카드의 가중치 합 DTO */
public record WeightSumResponseDTO(int def, int pro) {

    public static WeightSumResponseDTO from(List<CardResponse> cards) {

        int def = 0, pro = 0;

        // 카드가 없는 경우 둘 다 0
        if(cards == null || cards.isEmpty()) return new WeightSumResponseDTO(0, 0);

        for (CardResponse c : cards) {
            if(c.side() == Side.DEFENSE) def += c.weight();
            else pro += c.weight();
        }
        return new WeightSumResponseDTO(def, pro);
    }

    // 가중치 기반 난이도 계산
    public DifficultyResponseDTO toDifficulty() {

        return new DifficultyResponseDTO(
                Difficulty.of(def, pro),        // 변호사 난이도
                Difficulty.of(pro, def)         // 검사 난이도
        );
    }
}
