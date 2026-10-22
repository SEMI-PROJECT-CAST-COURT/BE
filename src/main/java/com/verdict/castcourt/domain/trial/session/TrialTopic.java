package com.verdict.castcourt.domain.trial.session;

/* 검사 카드의 공방 논점과 대응하는 변호인 카드. 직접 대응하는 근거가 없으면 null. */
public record TrialTopic(String topic, Integer defenseCardId) {
}
