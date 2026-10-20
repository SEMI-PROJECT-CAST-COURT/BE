package com.verdict.castcourt.domain.evidencecard.service;

import com.verdict.castcourt.domain.evidencecard.prompt.CardDraftPrompt;
import com.verdict.castcourt.domain.evidencecard.dto.CardDraftRequestDTO;
import com.verdict.castcourt.domain.evidencecard.dto.CardDraftResponseDTO;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/* AI 근거 카드 초안 (AI 사건 초안 · step = CARDS) : 요청 한 번에 카드 1장
 *  저장은 하지 않는다. 관리자가 고친 뒤 사건 등록(POST /api/admin/cases)으로 저장한다
 *  → 그때 EvidenceCardService 가 진영별 3장 이상 · 글자 수 · 가중치를 다시 검사한다.
 *  - validate() : 요청 스레드에서 부른다 (요청값 · 작품 확인)
 *  - generate() : 작업 스레드에서 부른다 (AI 호출)
 * */
@Slf4j
@Service
public class CardDraftService {

    // 카드 1장 JSON (한국어 90자 안팎) + 여유
    private static final int MAX_OUTPUT_TOKENS = 300;

    private static final Set<String> SIDES = Set.of("PROSECUTION", "DEFENSE");
    // 이미 만든 카드는 프롬프트에 다 들어가므로 상한을 둔다 (진영별 3장 이상이면 충분)
    private static final int MAX_EXISTING_CARDS = 20;

    // 사건 등록 검사(WorkCaseService.validateCase)와 같은 길이 제한
    private static final int QUESTION_MAX_LENGTH = 100;
    private static final int PERSON_MAX_LENGTH = 50;
    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final int PROGRESS_MAX_LENGTH = 1000;

    private final OpenAiClient openAiClient;
    private final WorkRepository workRepository;

    public CardDraftService(OpenAiClient openAiClient, WorkRepository workRepository) {
        this.openAiClient = openAiClient;
        this.workRepository = workRepository;
    }

    /* 요청값 확인 : 하나라도 틀리면 WS_DRAFT_REQUEST_INVALID (명세 : step · workId · 필수 옵션 불일치) */
    public void validate(CardDraftRequestDTO request) {

        boolean invalid = request.workId() <= 0
                || !SIDES.contains(request.side())
                || isInvalid(request.question(), QUESTION_MAX_LENGTH)
                || isInvalid(request.suspect(), PERSON_MAX_LENGTH)
                || isInvalid(request.victim(), PERSON_MAX_LENGTH)
                || isInvalid(request.summary(), SUMMARY_MAX_LENGTH)
                || isInvalid(request.progress(), PROGRESS_MAX_LENGTH)
                || request.cards().size() > MAX_EXISTING_CARDS
                || request.cards().stream().anyMatch(card -> !SIDES.contains(card.side())
                        || card.title().isBlank() || card.body().isBlank());

        if (invalid) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        // 없는 작품 · 삭제된 작품도 요청값 오류로 본다
        findWork(request.workId());
    }

    /* AI 로 근거 카드 1장을 만든다 · 실패는 OpenAiClient 의 AI_* 예외 그대로 (BaseWebSocketHandler 가 error 로 보낸다) */
    public CardDraftResponseDTO generate(CardDraftRequestDTO request) {

        Work work = findWork(request.workId());

        AiResult<JsonNode> response = openAiClient.createJson(
                CardDraftPrompt.INSTRUCTIONS,
                CardDraftPrompt.input(work, request),
                MAX_OUTPUT_TOKENS,
                "card_draft",
                CardDraftPrompt.SCHEMA
        );
        // TODO(연결) 8-4 response.usage() 를 ai_call_log 에 저장 (관리자 호출 : speaking_id · role = NULL)

        return toCard(request.side(), response.output());
    }

    /* AI 대답 → 카드
     *  - 제목 · 내용이 비면 쓸 수 없는 초안 → AI_UPSTREAM_ERROR (다시 받기)
     *  - 가중치는 1~20 으로 자른다
     *  - 글자 수 초과는 실패로 보지 않고 경고만 남긴다 (관리자가 고친 뒤 저장할 때 사건 등록 검사에 걸린다)
     * */
    private CardDraftResponseDTO toCard(String side, JsonNode json) {

        String title = json.path("title").asString("").strip();
        String body = json.path("body").asString("").strip();
        int weight = clampWeight(json.path("weight").asInt());

        if (title.isEmpty() || body.isEmpty()) {
            log.warn("[CardDraftService] 제목 또는 내용이 빈 카드 초안");
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }
        if (title.length() > CardDraftPrompt.TITLE_MAX_LENGTH || body.length() > CardDraftPrompt.BODY_MAX_LENGTH) {
            log.warn("[CardDraftService] 글자 수 초과 카드 : title {}자, body {}자", title.length(), body.length());
        }

        return new CardDraftResponseDTO(side, title, body, weight);
    }

    private Work findWork(int workId) {
        return workRepository.findActiveById(workId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID));
    }

    private boolean isInvalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private int clampWeight(int weight) {
        return Math.max(CardDraftPrompt.WEIGHT_MIN, Math.min(CardDraftPrompt.WEIGHT_MAX, weight));
    }
}
