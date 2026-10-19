package com.verdict.castcourt.domain.trial.service;

import com.verdict.castcourt.domain.trial.dto.MyTrialDTO;
import com.verdict.castcourt.domain.trial.dto.MyTrialListDTO;
import com.verdict.castcourt.domain.trial.dto.TrialOutcome;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.repository.MyTrialRow;
import com.verdict.castcourt.domain.trial.repository.TrialRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrialService {

    // 내 재판 목록 한 번에 보여 줄 개수 (명세에 없음 · 팀 확인 필요)
    static final int MY_TRIAL_PAGE_SIZE = 10;

    private final TrialRepository trialRepository;

    @Autowired
    public TrialService(TrialRepository trialRepository) {
        this.trialRepository = trialRepository;
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
}
