package com.verdict.castcourt.domain.workcase.ai.model;

import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.workcase.ai.prompt.CaseDraftAiPrompt;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiCaseDraftAi implements CaseDraftAi {

    private static final int CANDIDATES_MAX_OUTPUT_TOKENS = 4000;
    private static final int REWRITE_MAX_OUTPUT_TOKENS = 2000;
    private static final int CANDIDATE_COUNT = 3;

    private final OpenAiClient openAiClient;

    @Autowired
    public OpenAiCaseDraftAi(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @Override
    public AiResult<List<CaseDraftCandidateResponseDTO>> candidates(Work work, List<String> excludeQuestions) {

        // OpenAI 호출
        AiResult<JsonNode> response = openAiClient.createJson(
                CaseDraftAiPrompt.CANDIDATES,
                CaseDraftAiPrompt.candidatesInput(work, excludeQuestions),
                CANDIDATES_MAX_OUTPUT_TOKENS,
                "case_draft_candidates",
                CaseDraftAiPrompt.CANDIDATE_SCHEMA
        );

        // 응답 검사
        JsonNode candidates = response.output().path("candidates");
        if (!candidates.isArray() || candidates.size() != CANDIDATE_COUNT) {
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }

        // 결과 DTO를 담을 배열
        List<CaseDraftCandidateResponseDTO> results = new ArrayList<>();

        for (JsonNode candidate : candidates) {

            results.add(new CaseDraftCandidateResponseDTO(
                    requireText(candidate, "question", CaseDraftAiPrompt.MAX_QUESTION_LENGTH),
                    requireText(candidate, "suspect", CaseDraftAiPrompt.MAX_SUSPECT_LENGTH),
                    requireText(candidate, "victim", CaseDraftAiPrompt.MAX_VICTIM_LENGTH),
                    requireText(candidate, "summary", CaseDraftAiPrompt.MAX_SUMMARY_LENGTH),
                    requireText(candidate, "progress", CaseDraftAiPrompt.MAX_PROGRESS_LENGTH)
            ));
        }

        return new AiResult<>(results, response.usage());
    }

    @Override
    public AiResult<CaseDraftRewriteResponseDTO> rewrite(Work work, CaseDraftRequestDTO requestDTO) {

        // AI 호출
        AiResult<JsonNode> response = openAiClient.createJson(
                CaseDraftAiPrompt.REWRITE,
                CaseDraftAiPrompt.rewriteInput(work, requestDTO),
                REWRITE_MAX_OUTPUT_TOKENS,
                "case_draft_rewrite",
                CaseDraftAiPrompt.REWRITE_SCHEMA
        );

        JsonNode json = response.output();

        // 응답 검사 및 결과 DTO로 반환
        return new AiResult<>(
                new CaseDraftRewriteResponseDTO(
                        requireText(json, "summary", CaseDraftAiPrompt.MAX_SUMMARY_LENGTH),
                        requireText(json, "progress", CaseDraftAiPrompt.MAX_PROGRESS_LENGTH)
                ), response.usage());
    }

    // AI 응답 필드 검사
    private String requireText(JsonNode json, String field, int maxLength) {

        String text = json.path(field).asString("").strip();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }

        return text;
    }
}
