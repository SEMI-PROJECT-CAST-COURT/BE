package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.trial.dto.*;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.repository.MyTrialRow;
import com.verdict.castcourt.domain.trial.repository.TrialCaseReader;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.domain.trial.session.TrialTurnOrder;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public TrialService(TrialRepository trialRepository,
                        TrialCaseReader trialCaseReader,
                        TrialSessionStore trialSessionStore) {
        this.trialRepository = trialRepository;
        this.trialCaseReader = trialCaseReader;
        this.trialSessionStore = trialSessionStore;
    }

    // 재판 시작 (TRL-01) : 검사 → 스냅샷 · 카드 추첨 → 메모리에 저장 (DB 저장 없음)
    @Transactional(readOnly = true)
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
}