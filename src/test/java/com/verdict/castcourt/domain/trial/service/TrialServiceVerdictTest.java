package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.speaking.ai.VerdictAi;
import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import com.verdict.castcourt.domain.speaking.repository.SpeakingRepository;
import com.verdict.castcourt.domain.trial.dto.TrialVerdictResponseDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 판결 받기 (DB 없이 리포지토리 · 저장기를 가짜로, 보관소는 진짜로) */
@ExtendWith(MockitoExtension.class)
class TrialServiceVerdictTest {

    private static final String SUMMARY = "본 법정은 양측의 주장을 모두 검토하였습니다.";

    @Mock
    private TrialRepository trialRepository;

    @Mock
    private TrialVerdictWriter trialVerdictWriter;

    private final TrialSessionStore trialSessionStore = new TrialSessionStore();

    // 판사 총평 AI : 메서드가 하나뿐인 인터페이스라 람다로 만든다 (테스트마다 바꿔 끼운다)
    private VerdictAi verdictAi = session -> new AiResult<>(SUMMARY, AiUsage.fake(LocalDateTime.now(), 0));

    private TrialService trialService;

    @BeforeEach
    void setUp() {
        trialService = newService();
    }

    @Test
    @DisplayName("없는 재판 404, 남의 재판 403, 최후 변론 전 400, 이미 기록이 있는 역할 409")
    void checkErrors() {
        assertError(ErrorCode.TRIAL_NOT_FOUND, () -> trialService.receiveVerdict(4, "없는-id"));

        TrialSession unfinished = session(Side.DEFENSE);
        trialSessionStore.save(unfinished);
        assertError(ErrorCode.TRIAL_ACCESS_DENIED, () -> trialService.receiveVerdict(5, "trial-1"));
        assertError(ErrorCode.VERDICT_STAGE_INVALID, () -> trialService.receiveVerdict(4, "trial-1"));

        finish(unfinished);
        when(trialRepository.existsByUserIdAndWorkCaseIdAndRole(4, 7, Side.DEFENSE)).thenReturn(true);
        assertError(ErrorCode.ROLE_ALREADY_RECORDED, () -> trialService.receiveVerdict(4, "trial-1"));

        // 409 는 AI · 저장 전에 걸러진다
        verify(trialVerdictWriter, never()).save(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("판결하면 저장하고 메모리에서 지우며, 단계 이름 · 처분 문장 · 총평을 돌려준다")
    void verdict() {
        TrialSession session = finished(Side.DEFENSE);
        int tier = ScoreRule.penaltyTier(session.getScore());
        when(trialVerdictWriter.save(session, tier, SUMMARY)).thenReturn(savedTrial(session, tier));

        TrialVerdictResponseDTO response = trialService.receiveVerdict(4, "trial-1");

        assertEquals(10, response.trialId());
        assertEquals(session.getScore(), response.score());
        assertEquals(tier, response.penaltyTier());
        assertEquals(ScoreRule.tierTitle(tier), response.title());
        assertEquals("처분 " + tier, response.sentence());
        assertEquals(SUMMARY, response.judgeSummary());
        assertTrue(response.oppositeAvailable());           // 검사 기록이 없음 (가짜 리포지토리 기본값 false)
        assertTrue(trialSessionStore.findByTrialId("trial-1").isEmpty());
    }

    @Test
    @DisplayName("반대 역할 기록이 있으면 oppositeAvailable 은 false")
    void oppositeRecorded() {
        TrialSession session = finished(Side.DEFENSE);
        when(trialVerdictWriter.save(any(), anyInt(), anyString())).thenAnswer(invocation ->
                savedTrial(session, invocation.getArgument(1)));
        when(trialRepository.existsByUserIdAndWorkCaseIdAndRole(4, 7, Side.DEFENSE)).thenReturn(false);    // 내 역할은 기록 없음
        when(trialRepository.existsByUserIdAndWorkCaseIdAndRole(4, 7, Side.PROSECUTION)).thenReturn(true);  // 반대 역할은 기록 있음

        assertFalse(trialService.receiveVerdict(4, "trial-1").oppositeAvailable());
    }

    @Test
    @DisplayName("총평 AI 가 실패하면 저장하지 않고 재판은 메모리에 남는다 (다시 요청 가능)")
    void aiFail() {
        finished(Side.DEFENSE);
        verdictAi = session -> {
            throw new BusinessException(ErrorCode.AI_TIMEOUT);
        };
        trialService = newService();

        assertError(ErrorCode.AI_TIMEOUT, () -> trialService.receiveVerdict(4, "trial-1"));
        verify(trialVerdictWriter, never()).save(any(), anyInt(), anyString());
        assertTrue(trialSessionStore.findByTrialId("trial-1").isPresent());
    }

    @Test
    @DisplayName("저장 중 UNIQUE 에 걸리면 409, 재판은 메모리에 남는다")
    void duplicateSave() {
        finished(Side.DEFENSE);
        when(trialVerdictWriter.save(any(), anyInt(), anyString()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertError(ErrorCode.ROLE_ALREADY_RECORDED, () -> trialService.receiveVerdict(4, "trial-1"));
        assertTrue(trialSessionStore.findByTrialId("trial-1").isPresent());
    }

    @Test
    @DisplayName("저장기는 trial 1행과 발언 16행을 seq 순서로 만들고, 실제로 쓴 카드만 넣는다")
    void writer() {
        TrialRepository repository = mock(TrialRepository.class);
        SpeakingRepository speakingRepository = mock(SpeakingRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrialSession session = session(Side.PROSECUTION);
        finish(session);

        TrialEntity trial = new TrialVerdictWriter(repository, speakingRepository).save(session, 3, SUMMARY);

        assertEquals(session.usedCardIds(), trial.getCardIds());
        assertEquals(SUMMARY, trial.getJudgeSummary());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SpeakingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(speakingRepository).saveAll(captor.capture());
        List<SpeakingEntity> speakings = captor.getValue();
        assertEquals(16, speakings.size());
        assertEquals(IntStream.rangeClosed(1, 16).boxed().toList(), speakings.stream().map(SpeakingEntity::getSeq).toList());
        assertSame(trial, speakings.getFirst().getTrial());
    }

    // 생성자 주입 · SpeakingService 는 판결 받기에서 쓰지 않아서 null
    private TrialService newService() {
        return new TrialService(trialRepository, null, trialSessionStore, null, verdictAi, trialVerdictWriter);
    }

    // 카드 2장(11 · 12) · 형벌표 6단계 · userId 4 · 사건 7
    private static TrialSession session(Side role) {
        CaseSnapshot snapshot = new CaseSnapshot(
                7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "카드 1", "내용", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "카드 2", "내용", 12)),
                IntStream.rangeClosed(1, 6).mapToObj(tier -> new CaseSnapshot.Penalty(tier, "처분 " + tier)).toList());
        return new TrialSession("trial-1", 4, 7, role, TrialMode.IRON, snapshot, List.of(11, 12));
    }

    // 끝난 재판을 보관소에 넣는다
    private TrialSession finished(Side role) {
        TrialSession session = session(role);
        finish(session);
        trialSessionStore.save(session);
        return session;
    }

    // 16개 발언을 모두 기록한다 (①~④ 모두 충족 · 가중치 10)
    private static void finish(TrialSession session) {
        while (!session.isFinished()) {
            session.record("발언", true, true, true, true, 10);
        }
    }

    // DB 가 id 10 을 붙여 저장했다고 친 재판
    private static TrialEntity savedTrial(TrialSession session, int tier) {
        TrialEntity trial = new TrialEntity(session.getWorkCaseId(), session.getUserId(), session.getRole(),
                session.getMode(), session.usedCardIds(), session.getScore(), tier, SUMMARY, session.getSnapshot());
        ReflectionTestUtils.setField(trial, "id", 10);
        return trial;
    }

    private static void assertError(ErrorCode expected, Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        assertEquals(expected, e.getErrorCode());
    }
}
