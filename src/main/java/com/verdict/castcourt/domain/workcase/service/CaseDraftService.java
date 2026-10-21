package com.verdict.castcourt.domain.workcase.service;

import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.workcase.ai.model.CaseDraftAi;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CaseDraftService {

    private final WorkRepository workRepository;
    private final CaseDraftAi caseDraftAi;

    @Autowired
    public CaseDraftService(WorkRepository workRepository, CaseDraftAi caseDraftAi) {
        this.workRepository = workRepository;
        this.caseDraftAi = caseDraftAi;
    }

    /* 쟁점 후보 3개 (작업 스레드에서 실행) */
    public List<CaseDraftCandidateResponseDTO> candidates(int workId, List<String> excludeQuestions) {

        Work work = findWork(workId);

        // AI 호출
        AiResult<List<CaseDraftCandidateResponseDTO>> result = caseDraftAi.candidates(work, excludeQuestions);

        return result.output();
    }

    /* 피드백 반영해서 개요, 경과 다시 쓰기 */
    public CaseDraftRewriteResponseDTO rewrite(int workId, CaseDraftRequestDTO requestDTO) {

        Work work = findWork(workId);

        // AI 호출
        AiResult<CaseDraftRewriteResponseDTO> result = caseDraftAi.rewrite(work, requestDTO);

        return result.output();
    }

    // 작품 정보 불러오기
    private Work findWork(int workId) {

        return workRepository.findActiveById(workId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));
    }
}
