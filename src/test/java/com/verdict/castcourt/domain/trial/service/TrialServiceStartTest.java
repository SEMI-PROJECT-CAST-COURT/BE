package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.dto.TrialStartRequestDTO;
import com.verdict.castcourt.domain.trial.dto.TrialStartResponseDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/* 재판 시작 · 재판 상태 (DB 없이 사건 조회 · 리포지토리를 가짜로, 보관소는 진짜로) */
@ExtendWith(MockitoExtension.class)
class TrialServiceStartTest {

    @Mock
    private TrialRepository trialRepository;

    @Mock
    private TrialCaseReader trialCaseReader;

    @Spy
    private TrialSessionStore trialSessionStore = new TrialSessionStore();

    @InjectMocks
    private TrialService trialService;

    @Test
    @DisplayName("재판을 시작하면 보관소에 저장되고, 검사 카드 3장을 뽑는다")
    void start() {
        givenCase(7);

        TrialStartResponseDTO response = trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "IRON"));

        TrialSession session = trialSessionStore.findByUserId(4).orElseThrow();
        assertEquals(response.trialId(), session.getTrialId());
        assertEquals(Stage.OPENING, response.stage());
        assertEquals(3, session.getDrawnCardIds().size());
        assertTrue(List.of(11, 12, 13, 14).containsAll(session.getDrawnCardIds()));   // 검사 카드만
    }

    @Test
    @DisplayName("CHICKEN 은 내 쪽 카드만, CHALLENGER 는 사건 경과만 준다")
    void startVisibility() {
        givenCase(7);

        TrialStartResponseDTO chicken = trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "CHICKEN"));
        assertEquals(List.of(21, 22, 23), chicken.cards().stream().map(TrialStartResponseDTO.CardResponse::cardId).toList());
        assertNull(chicken.progress());

        TrialStartResponseDTO challenger = trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "CHALLENGER"));
        assertNull(challenger.cards());
        assertEquals("경과", challenger.progress());
    }

    @Test
    @DisplayName("역할 · 모드가 잘못되면 400, 사건이 없으면 404, 이미 기록이 있으면 409")
    void startErrors() {
        assertError(ErrorCode.TRIAL_ROLE_INVALID, () -> trialService.startTrial(4, new TrialStartRequestDTO(7, "JUDGE", "IRON")));
        assertError(ErrorCode.TRIAL_MODE_INVALID, () -> trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", null)));
        assertError(ErrorCode.CASE_NOT_FOUND, () -> trialService.startTrial(4, new TrialStartRequestDTO(null, "DEFENSE", "IRON")));

        when(trialCaseReader.findSnapshot(99)).thenReturn(Optional.empty());
        assertError(ErrorCode.CASE_NOT_FOUND, () -> trialService.startTrial(4, new TrialStartRequestDTO(99, "DEFENSE", "IRON")));

        givenCase(7);
        when(trialRepository.existsByUserIdAndWorkCaseIdAndRole(4, 7, Side.DEFENSE)).thenReturn(true);
        assertError(ErrorCode.ROLE_ALREADY_RECORDED, () -> trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "IRON")));
    }

    // 사건 caseId : 검사 카드 11~14, 변호인 카드 21~23
    private void givenCase(int caseId) {
        List<CaseSnapshot.Card> cards = List.of(
                card(11, Side.PROSECUTION), card(12, Side.PROSECUTION), card(13, Side.PROSECUTION), card(14, Side.PROSECUTION),
                card(21, Side.DEFENSE), card(22, Side.DEFENSE), card(23, Side.DEFENSE)
        );
        CaseSnapshot snapshot = new CaseSnapshot(caseId, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과", cards, List.of());
        when(trialCaseReader.findSnapshot(caseId)).thenReturn(Optional.of(snapshot));
    }

    private CaseSnapshot.Card card(int cardId, Side side) {
        return new CaseSnapshot.Card(cardId, side, "카드 " + cardId, "내용", 5);
    }

    private void assertError(ErrorCode expected, Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        assertEquals(expected, e.getErrorCode());
    }
}