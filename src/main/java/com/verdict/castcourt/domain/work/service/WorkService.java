package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;

import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class WorkService {
    private final WorkRepository works;
    private final WorkListRepository listRepository;
    private final CategoryRepository categories;
    private final PosterStorage posters;

    public WorkService(WorkRepository works, WorkListRepository listRepository,
                       CategoryRepository categories, PosterStorage posters) {
        this.works = works;
        this.listRepository = listRepository;
        this.categories = categories;
        this.posters = posters;
    }
    public Page<WorkListItem> getWorks(Integer categoryId, String keyword, String sort, int page) {
        if (page < 1 || page > Integer.MAX_VALUE / 8 || (categoryId != null && categoryId < 1)) {
            throw new BusinessException(ErrorCode.INVALID_WORK_LIST_QUERY);
        }
        WorkSort order;
        try {
            order = WorkSort.valueOf(sort);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_WORK_LIST_QUERY);
        }
        return listRepository.findWorks(categoryId, keyword == null ? "" : keyword.strip(), order, page);
    }

    public WorkDetailResponse getWork(Integer workId) {
        var work = works.findActiveById(workId).orElseThrow(() ->
                new BusinessException(ErrorCode.WORK_NOT_FOUND));
        return new WorkDetailResponse(work.getId(), work.getTitle(), work.getCategory().getName(),
                work.getAuthor(), work.getReleasedOn(), work.getSummary(), work.getPoster());
    }

    @Transactional
    public Integer create(WorkCreateRequest request, MultipartFile poster) {
        if (request == null || request.categoryId() == null || request.categoryId() < 1
                || invalidText(request.title(), 100) || invalidText(request.author(), 100)
                || request.releasedOn() == null
                || (request.summary() != null && request.summary().codePointCount(0, request.summary().length()) > 100))
            throw invalidInput();
        var category = categories.findById(request.categoryId()).orElseThrow(this::invalidInput);
        String title = request.title().strip();
        if (works.existsByTitle(title))
            throw new BusinessException(ErrorCode.WORK_TITLE_ALREADY_EXISTS);
        boolean uploaded = poster != null && !poster.isEmpty();
        String url = uploaded ? posters.save(poster) : "/images/default-poster.svg";
        if (uploaded && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) posters.delete(url);
                }
            });
        }
        try {
            return works.saveAndFlush(new Work(category, title, request.author().strip(),
                    request.releasedOn(), request.summary(), url)).getId();
        } catch (RuntimeException exception) {
            if (uploaded && !TransactionSynchronizationManager.isSynchronizationActive()) posters.delete(url);
            throw exception;
        }
    }

    @Transactional
    public void update(Integer workId, WorkUpdateRequest request, MultipartFile poster) {
        var work = works.findActiveById(workId).orElseThrow(() ->
                new BusinessException(ErrorCode.WORK_NOT_FOUND));
        if (request == null || request.categoryId() == null || request.categoryId() < 1
                || invalidText(request.title(), 100) || invalidText(request.author(), 100)
                || request.releasedOn() == null
                || (request.summary() != null
                    && request.summary().codePointCount(0, request.summary().length()) > 100)) {
            throw invalidInput();
        }
        var category = categories.findById(request.categoryId()).orElseThrow(this::invalidInput);
        String title = request.title().strip();
        if (works.existsByTitleAndIdNot(title, workId)) {
            throw new BusinessException(ErrorCode.WORK_TITLE_ALREADY_EXISTS);
        }
        boolean uploaded = poster != null && !poster.isEmpty();
        String url = uploaded ? posters.save(poster) : work.getPoster();
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (uploaded && synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) posters.delete(url);
                }
            });
        }
        try {
            work.update(category, title, request.author().strip(), request.releasedOn(),
                    request.summary(), url);
            works.flush();
        } catch (RuntimeException exception) {
            if (uploaded && !synchronizedTransaction) posters.delete(url);
            throw exception;
        }
    }

    @Transactional
    public void delete(Integer workId) {
        var work = works.findActiveById(workId).orElseThrow(() ->
                new BusinessException(ErrorCode.WORK_NOT_FOUND));
        // 사건 목록 API가 사건의 is_usage를 기준으로 조회하므로 함께 숨긴다.
        works.hideCasesByWorkId(workId);
        work.deactivate();
        works.flush();
    }

    private boolean invalidText(String value, int max) {
        return value == null || value.isBlank()
                || value.strip().codePointCount(0, value.strip().length()) > max;
    }

    private BusinessException invalidInput() {
        return new BusinessException(ErrorCode.WORK_INPUT_INVALID);
    }
}
