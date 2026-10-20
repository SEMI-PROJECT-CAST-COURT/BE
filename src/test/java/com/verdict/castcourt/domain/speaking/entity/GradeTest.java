package com.verdict.castcourt.domain.speaking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeTest {

    @Test
    @DisplayName("쟁점을 벗어나면 나머지를 모두 충족해도 OFF")
    void offWhenIssueMissed() {
        assertEquals(Grade.OFF, Grade.of(false, true, true, true));
    }

    @Test
    @DisplayName("쟁점과 나머지를 모두 충족하면 STRONG")
    void strongWhenAllMet() {
        assertEquals(Grade.STRONG, Grade.of(true, true, true, true));
    }

    @Test
    @DisplayName("쟁점 외 1개를 놓치면 OK")
    void okWhenOneMissed() {
        assertEquals(Grade.OK, Grade.of(true, true, false, true));
    }

    @Test
    @DisplayName("쟁점 외 2개 이상을 놓치면 WEAK")
    void weakWhenTwoMissed() {
        assertEquals(Grade.WEAK, Grade.of(true, false, false, true));
        assertEquals(Grade.WEAK, Grade.of(true, false, false, false));
    }

    @Test
    @DisplayName("첫 발언(반박 null)은 반박을 세지 않는다")
    void firstSpeakingIgnoresRebut() {
        assertEquals(Grade.STRONG, Grade.of(true, true, null, true));
        // 더미 데이터 trial 2 의 1번 발언과 같은 경우
        assertEquals(Grade.WEAK, Grade.of(true, false, null, false));
    }
}