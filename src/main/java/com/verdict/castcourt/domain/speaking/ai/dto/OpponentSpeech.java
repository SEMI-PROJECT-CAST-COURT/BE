package com.verdict.castcourt.domain.speaking.ai.dto;

import com.verdict.castcourt.domain.trial.rule.DifficultyRule;

import java.util.Set;

/* 상대 AI 발언 : 대사 + 이번 발언에 남기기로 한 허점 (판정에 그대로 반영한다) */
public record OpponentSpeech(
        String text,
        Set<DifficultyRule.Weakness> weaknesses
) {
}
