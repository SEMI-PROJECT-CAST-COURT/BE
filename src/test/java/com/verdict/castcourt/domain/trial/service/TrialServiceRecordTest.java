package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.speaking.repository.SpeakingRepository;
import com.verdict.castcourt.domain.trial.dto.response.TrialRecordResponseDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.repository.TrialCaseReader;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 나가기 · 재판 기록 · 사건 기록 요약의 검사 순서 (DB 없이 리포지토리 · 보관소를 가짜로) */
@ExtendWith(MockitoExtension.class)
class TrialServiceRecordTest {

    @Mock
    private TrialRepository trialRepository;

    @Mock
    private TrialCaseReader trialCaseReader;

    @Mock
    private TrialSessionStore trialSessionStore;

    @Mock
    private SpeakingRepository speakingRepository;

    @InjectMocks
    private TrialService trialService;

    @Test
    @DisplayName("나가기 : 이미 없는 판이면 아무 일 없이 성공한다 (멱등)")
    void exitMissing() {
        when(trialSessionStore.findByTrialId("t-1")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> trialService.exitTrial(4, "t-1"));
    }

    @Test
    @DisplayName("나가기 : 내 판이면 보관소에서 지운다")
    void exitMine() {
        TrialSession session = session(4);
        when(trialSessionStore.findByTrialId("t-1")).thenReturn(Optional.of(session));

        trialService.exitTrial(4, "t-1");

        verify(trialSessionStore).remove(session);
    }

    @Test
    @DisplayName("나가기 : 남의 판이면 403 이고 지우지 않는다")
    void exitOthers() {
        TrialSession session = session(5);
        when(trialSessionStore.findByTrialId("t-1")).thenReturn(Optional.of(session));

        BusinessException e = assertThrows(BusinessException.class, () -> trialService.exitTrial(4, "t-1"));

        assertEquals(ErrorCode.TRIAL_ACCESS_DENIED, e.getErrorCode());
        verify(trialSessionStore, never()).remove(session);
    }

    @Test
    @DisplayName("재판 기록 : 저장된 판이 없으면 404")
    void recordNotFound() {
        when(trialRepository.findById(10)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> trialService.getTrialRecord(4, 10));

        assertEquals(ErrorCode.TRIAL_RECORD_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("재판 기록 : 남의 판이면 403")
    void recordOthers() {
        when(trialRepository.findById(10)).thenReturn(Optional.of(trial(10, 5)));

        BusinessException e = assertThrows(BusinessException.class, () -> trialService.getTrialRecord(4, 10));

        assertEquals(ErrorCode.TRIAL_ACCESS_DENIED, e.getErrorCode());
    }

    @Test
    @DisplayName("재판 기록 : 내 판이면 단계 이름 · 처분 문장을 채워 돌려준다")
    void recordMine() {
        when(trialRepository.findById(10)).thenReturn(Optional.of(trial(10, 4)));
        when(speakingRepository.findByTrialIdOrderBySeqAsc(10)).thenReturn(List.of());

        TrialRecordResponseDTO result = trialService.getTrialRecord(4, 10);

        assertEquals("무죄 2단계", result.title());
        assertEquals("벌금 2", result.sentence());
    }

    @Test
    @DisplayName("사건 기록 요약 : 사건 행이 없으면 404")
    void caseRecordsNotFound() {
        when(trialCaseReader.existsCase(7)).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class, () -> trialService.getCaseRecords(4, 7));

        assertEquals(ErrorCode.CASE_NOT_FOUND, e.getErrorCode());
    }

    private TrialSession session(int userId) {
        return new TrialSession("t-1", userId, 7, Side.PROSECUTION, TrialMode.CHICKEN, null, List.of(11, 12));
    }

    // 저장된 재판 (id · userId 는 DB 가 채우는 값이라 테스트에서는 직접 넣는다)
    private TrialEntity trial(int id, int userId) {
        CaseSnapshot snapshot = new CaseSnapshot(7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(), List.of(new CaseSnapshot.Penalty(2, "벌금 2")));
        TrialEntity trial = new TrialEntity(null, null, Side.DEFENSE, TrialMode.CHICKEN, List.of(11, 12),
                25, 2, "총평", snapshot);
        ReflectionTestUtils.setField(trial, "id", id);
        ReflectionTestUtils.setField(trial, "userId", userId);
        return trial;
    }
}
