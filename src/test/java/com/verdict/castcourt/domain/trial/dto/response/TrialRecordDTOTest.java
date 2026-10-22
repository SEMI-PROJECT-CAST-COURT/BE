package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.repository.TrialStatRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/* 재판 기록 · 사건 기록 요약 · 내 통계 응답의 계산 */
class TrialRecordDTOTest {

    @Test
    @DisplayName("결정적 순간 : delta 절댓값이 큰 순, 같으면 seq 작은 쪽 먼저, 3개")
    void keyMoments() {
        List<SpeakingEntity> speakings = List.of(
                speaking(1, 3), speaking(2, -8), speaking(3, 5), speaking(4, 8), speaking(5, -5));

        assertEquals(List.of(2, 4, 3), TrialRecordResponseDTO.keyMoments(speakings));
    }

    @Test
    @DisplayName("사건 기록 요약 : 한 역할만 끝냈으면 다른 역할은 null, latest 는 끝낸 역할")
    void caseRecordsOneSide() {
        CaseRecordsResponseDTO result = CaseRecordsResponseDTO.of(List.of(trial(10, Side.DEFENSE)));

        assertEquals(10, result.def().trialId());
        assertNull(result.pro());
        assertEquals(Side.DEFENSE, result.latest());
    }

    @Test
    @DisplayName("사건 기록 요약 : 둘 다 있으면 latest 는 trialId 가 큰 쪽, 없으면 모두 null")
    void caseRecordsLatest() {
        CaseRecordsResponseDTO both = CaseRecordsResponseDTO.of(
                List.of(trial(12, Side.DEFENSE), trial(15, Side.PROSECUTION)));
        CaseRecordsResponseDTO none = CaseRecordsResponseDTO.of(List.of());

        assertEquals(Side.PROSECUTION, both.latest());
        assertNull(none.def());
        assertNull(none.latest());
    }

    @Test
    @DisplayName("내 통계 : 변호인은 1~3단계, 검사는 4~6단계면 승리 · 빈 단계는 0")
    void stats() {
        MyStatsResponseDTO result = MyStatsResponseDTO.from(List.of(
                row(Side.DEFENSE, 2, 3),        // 변호인 승 3
                row(Side.DEFENSE, 5, 1),        // 변호인 패 1
                row(Side.PROSECUTION, 4, 2)));  // 검사 승 2

        assertEquals(6, result.total());
        assertEquals(new MyStatsResponseDTO.RoleStats(4, 3, 1), result.byRole().def());
        assertEquals(new MyStatsResponseDTO.RoleStats(2, 2, 0), result.byRole().pro());
        assertEquals(List.of(0, 3, 0, 0, 1, 0), result.distribution().def());
        assertEquals(List.of(0, 0, 0, 2, 0, 0), result.distribution().pro());
    }

    private SpeakingEntity speaking(int seq, int delta) {
        return new SpeakingEntity(null, seq, Stage.OPENING, Side.PROSECUTION, Speaker.USER, "발언",
                true, true, null, true, false, Grade.OK, delta, 50);
    }

    private TrialEntity trial(int id, Side role) {
        TrialEntity trial = new TrialEntity(null, null, role, TrialMode.CHICKEN, List.of(11, 12), 40, 3, "총평", null);
        ReflectionTestUtils.setField(trial, "id", id);
        return trial;
    }

    private TrialStatRow row(Side role, int tier, long count) {
        return new TrialStatRow() {
            @Override
            public Side getRole() {
                return role;
            }

            @Override
            public Integer getPenaltyTier() {
                return tier;
            }

            @Override
            public Long getTrialCount() {
                return count;
            }
        };
    }
}
