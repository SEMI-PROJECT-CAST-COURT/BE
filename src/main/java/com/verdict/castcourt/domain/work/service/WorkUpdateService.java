package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.dto.WorkUpdateRequest;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkUpdateService {
    private final WorkRepository works;
    private final CategoryRepository categories;
    private final PosterStorage posters;

    public WorkUpdateService(WorkRepository works, CategoryRepository categories, PosterStorage posters) {
        this.works = works;
        this.categories = categories;
        this.posters = posters;
    }

    @Transactional
    public void update(Integer workId, WorkUpdateRequest request, MultipartFile poster) {
        var work = works.findActiveById(workId).orElseThrow(() ->
                new WorkListException(404, "WORK_NOT_FOUND", "작품을 찾을 수 없습니다."));
        if (request == null || request.categoryId() == null || request.categoryId() < 1
                || invalidText(request.title()) || invalidText(request.author())
                || request.releasedOn() == null
                || (request.summary() != null
                    && request.summary().codePointCount(0, request.summary().length()) > 100)) {
            throw invalidInput();
        }
        var category = categories.findById(request.categoryId()).orElseThrow(this::invalidInput);
        String title = request.title().strip();
        if (works.existsByTitleAndIdNot(title, workId)) {
            throw new WorkListException(409, "WORK_TITLE_ALREADY_EXISTS", "이미 등록된 작품명입니다.");
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

    private boolean invalidText(String value) {
        return value == null || value.isBlank()
                || value.strip().codePointCount(0, value.strip().length()) > 100;
    }

    private WorkListException invalidInput() {
        return new WorkListException(400, "WORK_INPUT_INVALID", "작품 정보를 확인해주세요");
    }
}