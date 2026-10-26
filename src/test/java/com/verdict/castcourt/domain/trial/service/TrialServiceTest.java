package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.trial.dto.response.MyTrialListDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.repository.MyTrialRow;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 내 재판 목록의 필터 검사 · cursor 계산 (DB 없이 리포지토리를 가짜로) */
@ExtendWith(MockitoExtension.class)
class TrialServiceTest {

    private static final int PAGE = TrialService.MY_TRIAL_PAGE_SIZE;

    @Mock
    private TrialRepository trialRepository;

    @InjectMocks
    private TrialService trialService;

    @Test
    @DisplayName("한 페이지보다 많으면 마지막으로 보여 준 trialId 가 nextCursor 가 된다")
    void nextCursorWhenMore() {
        // trialId 20, 19, ... 10 (PAGE + 1 개)
        when(trialRepository.findMyTrials(4, null, null, null, null, Limit.of(PAGE + 1)))
                .thenReturn(rows(20, PAGE + 1));

        MyTrialListDTO result = trialService.getMyTrials(4, null, null, null, null);

        assertEquals(PAGE, result.items().size());
        assertEquals("11", result.nextCursor());
    }

    @Test
    @DisplayName("한 페이지 이하이면 nextCursor 는 null")
    void lastPage() {
        when(trialRepository.findMyTrials(4, null, null, null, 11, Limit.of(PAGE + 1)))
                .thenReturn(rows(10, 3));

        MyTrialListDTO result = trialService.getMyTrials(4, null, null, null, "11");

        assertEquals(3, result.items().size());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("필터 값은 문자열로 리포지토리에 넘긴다")
    void filtersPassed() {
        when(trialRepository.findMyTrials(4, Side.DEFENSE, "WIN", 1, null, Limit.of(PAGE + 1)))
                .thenReturn(List.of());

        MyTrialListDTO result = trialService.getMyTrials(4, "DEFENSE", "WIN", 1, "");

        assertTrue(result.items().isEmpty());
        verify(trialRepository).findMyTrials(4, Side.DEFENSE, "WIN", 1, null, Limit.of(PAGE + 1));
    }

    @Test
    @DisplayName("모르는 역할 · 승패 · 0 이하 작품 id 는 INVALID_TRIAL_LIST_FILTER")
    void invalidFilter() {
        assertError(ErrorCode.INVALID_TRIAL_LIST_FILTER, () -> trialService.getMyTrials(4, "JUDGE", null, null, null));
        assertError(ErrorCode.INVALID_TRIAL_LIST_FILTER, () -> trialService.getMyTrials(4, null, "DRAW", null, null));
        assertError(ErrorCode.INVALID_TRIAL_LIST_FILTER, () -> trialService.getMyTrials(4, null, null, 0, null));
    }

    @Test
    @DisplayName("숫자가 아니거나 1 미만인 cursor 는 INVALID_CURSOR")
    void invalidCursor() {
        assertError(ErrorCode.INVALID_CURSOR, () -> trialService.getMyTrials(4, null, null, null, "abc"));
        assertError(ErrorCode.INVALID_CURSOR, () -> trialService.getMyTrials(4, null, null, null, "0"));
    }

    // trialId 가 start 부터 1씩 줄어드는 가짜 행 count 개
    private List<MyTrialRow> rows(int start, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> row(start - i))
                .toList();
    }

    private MyTrialRow row(int trialId) {
        return new MyTrialRow() {
            public Integer getTrialId() { return trialId; }
            public CaseSnapshot getCaseSnapshot() {
                return new CaseSnapshot(7, 1, "작품", "쟁점", null, null, null, null, List.of(), List.of());
            }
            public Side getRole() { return Side.DEFENSE; }
            public Integer getPenaltyTier() { return 3; }
            public Boolean getCaseUsage() { return true; }
            public Boolean getWorkUsage() { return true; }
        };
    }

    private void assertError(ErrorCode expected, Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        assertEquals(expected, e.getErrorCode());
    }
}
