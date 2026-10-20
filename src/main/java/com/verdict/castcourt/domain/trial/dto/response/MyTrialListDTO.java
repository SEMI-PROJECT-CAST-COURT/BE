package com.verdict.castcourt.domain.trial.dto.response;

import java.util.List;

/* 내 재판 목록 응답 · 마지막 페이지면 nextCursor = null */
public record MyTrialListDTO(
        List<MyTrialDTO> items,
        String nextCursor
) {
}
