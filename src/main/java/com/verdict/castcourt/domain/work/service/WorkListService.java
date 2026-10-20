package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.repository.WorkListRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkListService {
    private final WorkListRepository repository;
    public WorkListService(WorkListRepository repository) { this.repository = repository; }

    public Page<WorkListItem> getWorks(Integer categoryId, String keyword, String sort, int page) {
        if (page < 1 || page > Integer.MAX_VALUE / 8 || (categoryId != null && categoryId < 1)) {
            throw new WorkListException(400, "INVALID_WORK_LIST_QUERY", "작품 목록 검색 조건을 확인해 주세요.");
        }
        WorkSort order;
        try {
            order = WorkSort.valueOf(sort);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new WorkListException(400, "INVALID_WORK_LIST_QUERY", "작품 목록 검색 조건을 확인해 주세요.");
        }
        return repository.findWorks(categoryId, keyword == null ? "" : keyword.strip(), order, page);
    }
}