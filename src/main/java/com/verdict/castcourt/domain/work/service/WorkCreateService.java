package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.dto.WorkCreateRequest;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkCreateService {
    private final WorkRepository works;
    private final CategoryRepository categories;
    private final PosterStorage posters;

    public WorkCreateService(WorkRepository works, CategoryRepository categories, PosterStorage posters) {
        this.works = works;
        this.categories = categories;
        this.posters = posters;
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
            throw new WorkListException(409, "WORK_TITLE_ALREADY_EXISTS", "이미 등록된 작품명입니다.");
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

    private boolean invalidText(String value, int max) {
        return value == null || value.isBlank()
                || value.strip().codePointCount(0, value.strip().length()) > max;
    }
    private WorkListException invalidInput() {
        return new WorkListException(400, "WORK_INPUT_INVALID", "작품 정보를 확인해주세요");
    }
}