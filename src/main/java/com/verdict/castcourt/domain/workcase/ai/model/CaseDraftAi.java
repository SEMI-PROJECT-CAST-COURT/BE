package com.verdict.castcourt.domain.workcase.ai.model;

import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;

import java.util.List;

public interface CaseDraftAi {

    /* 사건 초안 생성 */
    AiResult<List<CaseDraftCandidateResponseDTO>> candidates(Work work, List<String> excludeQuestions);

    /* 개요, 경과 다시 쓰기 */
    AiResult<CaseDraftRewriteResponseDTO> rewrite(Work work, CaseDraftRequestDTO requestDTO);
}
