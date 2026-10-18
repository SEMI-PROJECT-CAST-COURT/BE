package com.verdict.castcourt.domain.speaking.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* 발언 등급 (speaking.grade) · 배율은 기획서 확정값 1 / 0.6 / 0.3 / 0 */
@Getter
@RequiredArgsConstructor
public enum Grade {

    STRONG(100),    // 강함 : ① 쟁점과 나머지 모두 충족
    OK(60),         // 보통 : ① 외 1개 미충족
    WEAK(30),       // 약함 : ① 외 2개 이상 미충족
    OFF(0);         // 무관 : ① 쟁점 이탈

    // 배율을 백분율 정수로 둔다 (0.6 같은 소수 계산 오차를 피하려고)
    private final int percent;
}