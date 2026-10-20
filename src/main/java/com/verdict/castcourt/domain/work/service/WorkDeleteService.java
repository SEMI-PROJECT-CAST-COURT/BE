package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkDeleteService {
    private final WorkRepository works;
    public WorkDeleteService(WorkRepository works) { this.works = works; }

    @Transactional
    public void delete(Integer workId) {
        var work = works.findActiveById(workId).orElseThrow(() ->
                new WorkListException(404, "WORK_NOT_FOUND", "작품을 찾을 수 없습니다."));
        // 사건 목록 API가 사건의 is_usage를 기준으로 조회하므로 함께 숨긴다.
        works.hideCasesByWorkId(workId);
        work.deactivate();
        works.flush();
    }
}