package com.verdict.castcourt.domain.trial.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.verdict.castcourt.domain.trial.dto.response.TrialStatusResponseDTO;
import com.verdict.castcourt.domain.speaking.ai.JudgeResult;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.service.SpeakingService;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.dto.request.TrialStartRequestDTO;
import com.verdict.castcourt.domain.trial.dto.response.TrialStartResponseDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.repository.TrialCaseReader;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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

    private static final AiUsage USAGE = new AiUsage("test-model", 0, 0, LocalDateTime.now(), 0);

    // 진짜 SpeakingService 에 람다 AI 를 넣는다 (판사는 ①~④ 모두 충족, 상대는 정해진 문장 · Runnable::run = 작업을 바로 실행)
    @Spy
    private SpeakingService speakingService = new SpeakingService(
            new TrialSessionStore(),
            (session, content) -> new AiResult<>(JudgeResult.of(session, true, true, true, true, 10), USAGE),
            (session, onDelta) -> new AiResult<>("재판장님, 검사 측 모두 진술입니다.", USAGE),
            Runnable::run);

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

    @Test
    @DisplayName("6-6 변호인으로 시작하면 AI 검사 모두 진술이 기록되고 openingSpeaking 으로 온다, 검사면 없다")
    void aiOpening() {
        givenCase(7);

        TrialStartResponseDTO defense = trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "IRON"));
        TrialSession session = trialSessionStore.findByUserId(4).orElseThrow();
        assertEquals(1, session.getSpeakings().size());
        assertEquals(Speaker.AI, session.getSpeakings().get(0).speaker());
        assertEquals(session.getSpeakings().get(0).content(), defense.openingSpeaking());
        assertEquals(Speaker.USER, session.currentTurn().speaker());   // 이제 내 차례 (seq 2)

        TrialStartResponseDTO prosecution = trialService.startTrial(4, new TrialStartRequestDTO(7, "PROSECUTION", "IRON"));
        assertNull(prosecution.openingSpeaking());
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

    @Test
    @DisplayName("재판 상태 : 메모리에 없으면 404, 남의 재판이면 403")
    void statusErrors() {
        givenCase(7);
        String trialId = trialService.startTrial(4, new TrialStartRequestDTO(7, "DEFENSE", "IRON")).trialId();

        assertError(ErrorCode.TRIAL_NOT_FOUND, () -> trialService.getTrialStatus(4, "없는-id"));
        assertError(ErrorCode.TRIAL_ACCESS_DENIED, () -> trialService.getTrialStatus(5, trialId));
    }

    @Test
    @DisplayName("재판 상태 : 공방 중이면 카드 · 공방 번호, IRON 은 게이지 · 판정을 숨긴다")
    void status() {
        givenCase(7);
        String trialId = trialService.startTrial(4, new TrialStartRequestDTO(7, "PROSECUTION", "IRON")).trialId();
        TrialSession session = trialSessionStore.findByTrialId(trialId).orElseThrow();
        for (int i = 0; i < 4; i++) {           // 모두 진술 2 + 카드 1 의 첫 공방 2 → 다음은 seq 5
            session.record("발언", true, true, i == 0 ? null : true, true, 10);
        }

        TrialStatusResponseDTO status = trialService.getTrialStatus(4, trialId);

        assertEquals(Stage.CARD, status.stage());
        assertEquals(1, status.cardNo());
        assertEquals(2, status.exchangeNo());
        assertNull(status.score());
        assertEquals(4, status.speakings().size());
        assertNotNull(status.speakings().get(0).content());
        assertNull(status.speakings().get(0).grade());
        assertNull(status.speakings().get(0).scoreAfter());
    }
}
