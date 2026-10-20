package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.domain.work.dto.WorkDetailResponse;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkDetailService {
    private final WorkRepository repository;
    public WorkDetailService(WorkRepository repository) { this.repository = repository; }

    public WorkDetailResponse getWork(Integer workId) {
        var work = repository.findActiveById(workId).orElseThrow(() ->
                new WorkListException(404, "WORK_NOT_FOUND", "작품을 찾을 수 없습니다."));
        return new WorkDetailResponse(work.getId(), work.getTitle(), work.getCategory().getName(),
                work.getAuthor(), work.getReleasedOn(), work.getSummary(), work.getPoster());
    }
}