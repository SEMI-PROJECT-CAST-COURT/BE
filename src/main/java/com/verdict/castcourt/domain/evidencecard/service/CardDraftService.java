package com.verdict.castcourt.domain.evidencecard.service;

import com.verdict.castcourt.domain.evidencecard.dto.CardSuggestResponseDTO;
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

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class CardDraftService {

    // 카드 1장 JSON (한국어 90자 안팎) + 여유
    private static final int DRAFT_MAX_OUTPUT_TOKENS = 300;
    // 제안 문구 한 줄 + 카드 1장 + 여유
    private static final int SUGGEST_MAX_OUTPUT_TOKENS = 500;

    private static final Set<String> SIDES = Set.of("PROSECUTION", "DEFENSE");
    // 카드 목록은 프롬프트에 다 들어가므로 상한을 둔다 (진영별 3장 이상이면 충분)
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

    /* CARDS 요청값 확인 : 공통 + 만들 진영 */
    public void validateDraft(CardDraftRequestDTO request) {
        if (!SIDES.contains(request.side())) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        validateCommon(request);
    }

    /* CARD_SUGGEST 요청값 확인 : 공통 + 검토할 카드 */
    public void validateSuggest(CardDraftRequestDTO request) {
        if (request.card() == null || isInvalidCard(request.card())) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        validateCommon(request);
    }

    /* 새 카드 1장 (CARDS) */
    public CardDraftResponseDTO generate(CardDraftRequestDTO request) {

        Work work = findWork(request.workId());
        JsonNode json = callAi(CardDraftPrompt.DRAFT_INSTRUCTIONS, CardDraftPrompt.draftInput(work, request),
                DRAFT_MAX_OUTPUT_TOKENS, "card_draft", CardDraftPrompt.DRAFT_SCHEMA);

        String title = text(json, "title");
        String body = text(json, "body");
        if (title.isEmpty() || body.isEmpty()) {
            log.warn("[CardDraftService] 제목 또는 내용이 빈 카드");
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }
        warnIfTooLong(title, body);

        return new CardDraftResponseDTO(request.side(), title, body, clampWeight(json.path("weight").asInt()));
    }

    /* 수정 제안 1장 (CARD_SUGGEST) · 진영은 검토할 카드 그대로 */
    public CardSuggestResponseDTO suggest(CardDraftRequestDTO request) {

        Work work = findWork(request.workId());
        JsonNode json = callAi(CardDraftPrompt.SUGGEST_INSTRUCTIONS, CardDraftPrompt.suggestInput(work, request),
                SUGGEST_MAX_OUTPUT_TOKENS, "card_suggest", CardDraftPrompt.SUGGEST_SCHEMA);

        CardDraftRequestDTO.Card original = request.card();
        String message = text(json, "message");
        String title = text(json, "title");
        String body = text(json, "body");
        int weight = clampWeight(json.path("weight").asInt());

        // 제안 문구가 없거나 고친 카드가 비면 쓸 수 없는 제안 → AI_UPSTREAM_ERROR (다시 받기)
        if (message.isEmpty() || title.isEmpty() || body.isEmpty()) {
            log.warn("[CardDraftService] 비어 있는 수정 제안");
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
        }
        if (message.length() > CardDraftPrompt.MESSAGE_MAX_LENGTH) {
            log.warn("[CardDraftService] 제안 문구 글자 수 초과 : {}자", message.length());
        }
        warnIfTooLong(title, body);

        // [적용] 버튼을 보여 줄지 화면이 정할 수 있게, 원래 카드와 달라졌는지 서버가 비교한다
        boolean changed = !title.equals(original.title())
                || !body.equals(original.body())
                || weight != original.weight();

        return new CardSuggestResponseDTO(original.side(), message, title, body, weight, changed);
    }

    /* 두 step 공통 : workId · 사건 필드 · 카드 목록 · 작품 존재
     *  하나라도 틀리면 WS_DRAFT_REQUEST_INVALID (명세 : step · workId · 필수 옵션 불일치)
     * */
    private void validateCommon(CardDraftRequestDTO request) {

        boolean invalid = request.workId() <= 0
                || isInvalid(request.question(), QUESTION_MAX_LENGTH)
                || isInvalid(request.suspect(), PERSON_MAX_LENGTH)
                || isInvalid(request.victim(), PERSON_MAX_LENGTH)
                || isInvalid(request.summary(), SUMMARY_MAX_LENGTH)
                || isInvalid(request.progress(), PROGRESS_MAX_LENGTH)
                || request.cards().size() > MAX_EXISTING_CARDS
                || request.cards().stream().anyMatch(this::isInvalidCard);

        if (invalid) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        // 없는 작품 · 삭제된 작품도 요청값 오류로 본다
        findWork(request.workId());
    }

    /* AI 호출 · 실패는 OpenAiClient 의 AI_* 예외 그대로 (BaseWebSocketHandler 가 error 로 보낸다) */
    private JsonNode callAi(String instructions, String input, int maxOutputTokens,
                            String schemaName, Map<String, Object> schema) {

        AiResult<JsonNode> response = openAiClient.createJson(instructions, input, maxOutputTokens, schemaName, schema);
        // TODO(연결) 8-4 response.usage() 를 ai_call_log 에 저장 (관리자 호출 : speaking_id · role = NULL)
        return response.output();
    }

    // 글자 수 초과는 실패로 보지 않고 경고만 남긴다 (관리자가 고친 뒤 저장할 때 사건 등록 검사에 걸린다)
    private void warnIfTooLong(String title, String body) {
        if (title.length() > CardDraftPrompt.TITLE_MAX_LENGTH || body.length() > CardDraftPrompt.BODY_MAX_LENGTH) {
            log.warn("[CardDraftService] 글자 수 초과 카드 : title {}자, body {}자", title.length(), body.length());
        }
    }

    private Work findWork(int workId) {
        return workRepository.findActiveById(workId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID));
    }

    // 카드 한 장 : 진영이 맞고 제목 · 내용이 있어야 한다 (글자 수는 저장할 때 검사)
    private boolean isInvalidCard(CardDraftRequestDTO.Card card) {
        return !SIDES.contains(card.side()) || card.title().isBlank() || card.body().isBlank();
    }

    private boolean isInvalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private String text(JsonNode json, String field) {
        return json.path(field).asString("").strip();
    }

    // 가중치는 1~20 으로 자른다
    private int clampWeight(int weight) {
        return Math.max(CardDraftPrompt.WEIGHT_MIN, Math.min(CardDraftPrompt.WEIGHT_MAX, weight));
    }
}