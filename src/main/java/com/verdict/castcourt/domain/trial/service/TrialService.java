package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.speaking.ai.VerdictAi;
import com.verdict.castcourt.domain.trial.dto.TrialStatusResponseDTO;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.service.SpeakingService;
import com.verdict.castcourt.domain.trial.dto.MyTrialDTO;
import com.verdict.castcourt.domain.trial.dto.MyTrialListDTO;
import com.verdict.castcourt.domain.trial.dto.TrialOutcome;
import com.verdict.castcourt.domain.trial.dto.TrialStartRequestDTO;
import com.verdict.castcourt.domain.trial.dto.TrialStartResponseDTO;
import com.verdict.castcourt.domain.trial.dto.TrialVerdictResponseDTO;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.repository.MyTrialRow;
import com.verdict.castcourt.domain.trial.repository.TrialCaseReader;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.domain.trial.session.TrialTurnOrder;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class TrialService {

    // 내 재판 목록 한 번에 보여 줄 개수 (명세에 없음 · 팀 확인 필요)
    static final int MY_TRIAL_PAGE_SIZE = 10;

    private final TrialRepository trialRepository;
    private final TrialCaseReader trialCaseReader;
    private final TrialSessionStore trialSessionStore;
    private final SpeakingService speakingService;     // 변호인 시작 때 AI 검사 모두 진술 (6-6)
    private final VerdictAi verdictAi;                 // 판사 총평 (7-2)
    private final TrialVerdictWriter trialVerdictWriter;   // 판결 저장 트랜잭션 (7-3)

    @Autowired
    public TrialService(TrialRepository trialRepository,
                        TrialCaseReader trialCaseReader,
                        TrialSessionStore trialSessionStore,
                        SpeakingService speakingService,
                        VerdictAi verdictAi,
                        TrialVerdictWriter trialVerdictWriter) {
        this.trialRepository = trialRepository;
        this.trialCaseReader = trialCaseReader;
        this.trialSessionStore = trialSessionStore;
        this.speakingService = speakingService;
        this.verdictAi = verdictAi;
        this.trialVerdictWriter = trialVerdictWriter;
    }

    /* 재판 시작 (TRL-01) : 검사 → 스냅샷 · 카드 추첨 → (변호인이면 AI 검사 모두 진술) → 메모리에 저장 (DB 저장 없음)
     *  @Transactional 을 붙이지 않는다. AI 호출(몇 초)을 하는 동안 DB 연결을 붙잡고 있지 않게 하려고.
     *  리포지토리 메서드는 각자 짧은 트랜잭션으로 실행된다.
     * */
    public TrialStartResponseDTO startTrial(int userId, TrialStartRequestDTO request) {

        Side role = parseRequired(Side.class, request.role(), ErrorCode.TRIAL_ROLE_INVALID);
        TrialMode mode = parseRequired(TrialMode.class, request.mode(), ErrorCode.TRIAL_MODE_INVALID);
        if (request.caseId() == null) {
            throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
        }
        int caseId = request.caseId();

        CaseSnapshot snapshot = trialCaseReader.findSnapshot(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        if (trialRepository.existsByUserIdAndWorkCaseIdAndRole(userId, caseId, role)) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_RECORDED);
        }

        TrialSession session = new TrialSession(
                UUID.randomUUID().toString(),
                userId,
                caseId,
                role,
                mode,
                snapshot,
                drawCards(snapshot)
        );

        // 변호인이면 첫 차례가 AI 검사다. 저장 전에 만들어서, AI 가 실패하면 판이 생기지 않게 한다 (502 · 503 · 504 JSON 응답)
        if (session.currentTurn().speaker() == Speaker.AI) {
            speakingService.playAiOpening(session);
        }

        trialSessionStore.save(session);    // 같은 사용자의 이전 판은 덮어쓴다

        return TrialStartResponseDTO.from(session);
    }

    // 내 재판 목록 (TRL-08)
    @Transactional(readOnly = true)
    public MyTrialListDTO getMyTrials(int userId, String role, String outcome, Integer workId, String cursor) {

        Side roleFilter = parseFilter(Side.class, role);
        TrialOutcome outcomeFilter = parseFilter(TrialOutcome.class, outcome);
        if (workId != null && workId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_TRIAL_LIST_FILTER);
        }
        Integer lastTrialId = parseCursor(cursor);

        // 다음 페이지가 있는지 알려고 1개 더 읽는다
        List<MyTrialRow> rows = trialRepository.findMyTrials(
                userId,
                roleFilter == null ? null : roleFilter.name(),
                outcomeFilter == null ? null : outcomeFilter.name(),
                workId,
                lastTrialId,
                MY_TRIAL_PAGE_SIZE + 1
        );

        boolean hasNext = rows.size() > MY_TRIAL_PAGE_SIZE;

        List<MyTrialDTO> items = rows.stream()
                .limit(MY_TRIAL_PAGE_SIZE)
                .map(MyTrialDTO::from)
                .toList();

        String nextCursor = hasNext ? String.valueOf(items.getLast().trialId()) : null;

        return new MyTrialListDTO(items, nextCursor);
    }

    // 필터 문자열 → enum · 비어 있으면 null(필터 없음) · 모르는 값이면 INVALID_TRIAL_LIST_FILTER
    private <E extends Enum<E>> E parseFilter(Class<E> type, String value) {

        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TRIAL_LIST_FILTER);
        }
    }

    // 공방 카드 추첨 : 검사 카드를 섞어 앞에서 3장 (3번째는 조건이 맞을 때만 쓴다 · TrialSession)
    private List<Integer> drawCards(CaseSnapshot snapshot) {

        List<Integer> cardIds = new ArrayList<>(snapshot.cards().stream()
                .filter(card -> card.side() == Side.PROSECUTION)
                .map(CaseSnapshot.Card::cardId)
                .toList());
        Collections.shuffle(cardIds);

        return cardIds.subList(0, Math.min(TrialTurnOrder.MAX_CARD_COUNT, cardIds.size()));
    }

    // 필수 문자열 → enum · 비어 있거나 모르는 값이면 errorCode
    private <E extends Enum<E>> E parseRequired(Class<E> type, String value, ErrorCode errorCode) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode);
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(errorCode);
        }
    }

    // cursor = 앞 페이지 마지막 trialId · 비어 있으면 첫 페이지 · 숫자가 아니거나 1 미만이면 INVALID_CURSOR
    private Integer parseCursor(String cursor) {

        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            int lastTrialId = Integer.parseInt(cursor);
            if (lastTrialId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            return lastTrialId;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    // 재판 상태 (TRL-02) : 메모리에 없으면 404, 남의 재판이면 403
    public TrialStatusResponseDTO getTrialStatus(int userId, String trialId) {

        TrialSession session = trialSessionStore.findByTrialId(trialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIAL_NOT_FOUND));

        if (session.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TRIAL_ACCESS_DENIED);
        }

        return TrialStatusResponseDTO.from(session);
    }

    /* 판결 받기 (TRL-03) : 검사 → 판사 총평(AI) → 저장(트랜잭션) → 메모리 정리
     *  startTrial 과 같은 이유로 @Transactional 을 붙이지 않는다. 트랜잭션은 저장(TrialVerdictWriter)에만 건다.
     *  AI · 저장이 실패하면 메모리의 재판은 그대로 남아서, 같은 요청을 다시 보내면 된다.
     * */
    public TrialVerdictResponseDTO receiveVerdict(int userId, String trialId) {

        TrialSession session = checkVerdictable(userId, trialId);

        int penaltyTier = ScoreRule.penaltyTier(session.getScore());
        String judgeSummary = summarize(session);
        TrialEntity trial = save(session, penaltyTier, judgeSummary);

        trialSessionStore.remove(session);      // 저장이 끝난 판은 메모리에서 지운다 (이후 재판 상태 조회는 404)

        boolean oppositeAvailable = !trialRepository.existsByUserIdAndWorkCaseIdAndRole(
                userId, session.getWorkCaseId(), session.getRole().opposite());

        return TrialVerdictResponseDTO.of(trial, oppositeAvailable);
    }

    /* 판결 전 검사 : 404 → 403 → 최후 변론까지 끝났는지(400) → 이미 기록이 있는 역할인지(409)
     *  409 는 AI 를 부르기 전에 검사해서 저장하지 못할 총평에 비용을 쓰지 않는다.
     * */
    TrialSession checkVerdictable(int userId, String trialId) {

        TrialSession session = trialSessionStore.findByTrialId(trialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIAL_NOT_FOUND));

        if (session.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TRIAL_ACCESS_DENIED);
        }

        if (!session.isFinished()) {
            throw new BusinessException(ErrorCode.VERDICT_STAGE_INVALID);
        }

        if (trialRepository.existsByUserIdAndWorkCaseIdAndRole(userId, session.getWorkCaseId(), session.getRole())) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_RECORDED);
        }
        return session;
    }

    /* 판사 총평 (트랜잭션 밖) : AI 를 부르는 몇 초 동안 DB 연결을 붙잡지 않는다
     *  실패하면 OpenAiClient 의 502 · 503 · 504 가 그대로 JSON 오류 응답이 된다.
     *  판사 총평은 ai_call_log 집계 대상이 아니라 usage 는 쓰지 않는다. (명세 v6 비고)
     * */
    String summarize(TrialSession session) {
        return verdictAi.summarize(session).output();
    }

    /* 저장 (트랜잭션 안) : 같은 판결이 동시에 두 번 들어와 UNIQUE(user_id, work_case_id, role) 에 걸리면 409 */
    TrialEntity save(TrialSession session, int penaltyTier, String judgeSummary) {
        try {
            return trialVerdictWriter.save(session, penaltyTier, judgeSummary);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_RECORDED);
        }
    }
}
