package com.verdict.castcourt.domain.penaltytier.service;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyDraftRequestDTO;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyDraftResponseDTO;
import com.verdict.castcourt.domain.penaltytier.prompt.PenaltyDraftPrompt;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Slf4j
@Service
public class PenaltyDraftService {
    // 처분 6개 (한국어 50자 이내) JSON + 여유
    private static final int MAX_OUTPUT_TOKENS = 600;

    // 이전 처분은 프롬프트에 다 들어가므로 상한을 둔다 (다시 만들기 10번 = 60개)
    private static final int MAX_EXCLUDE_SENTENCES = 60;
    private static final int EXCLUDE_SENTENCE_MAX_LENGTH = 100;

    // 사건 등록 검사(WorkCaseService.validateCase)와 같은 길이 제한
    private static final int QUESTION_MAX_LENGTH = 100;
    private static final int PERSON_MAX_LENGTH = 50;
    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final int PROGRESS_MAX_LENGTH = 1000;

    private final OpenAiClient openAiClient;
    private final WorkRepository workRepository;

    public PenaltyDraftService(OpenAiClient openAiClient, WorkRepository workRepository) {
        this.openAiClient = openAiClient;
        this.workRepository = workRepository;
    }

    /* 요청값 확인 : 하나라도 틀리면 WS_DRAFT_REQUEST_INVALID (명세 : step · workId · 필수 옵션 불일치) */
    public void validate(PenaltyDraftRequestDTO request) {

        boolean invalid = request.workId() <= 0
                || isInvalid(request.question(), QUESTION_MAX_LENGTH)
                || isInvalid(request.suspect(), PERSON_MAX_LENGTH)
                || isInvalid(request.victim(), PERSON_MAX_LENGTH)
                || isInvalid(request.summary(), SUMMARY_MAX_LENGTH)
                || isInvalid(request.progress(), PROGRESS_MAX_LENGTH)
                || request.excludeSentences().size() > MAX_EXCLUDE_SENTENCES
                || request.excludeSentences().stream().anyMatch(s -> s.length() > EXCLUDE_SENTENCE_MAX_LENGTH);

        if (invalid) {
            throw new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID);
        }
        // 없는 작품 · 삭제된 작품도 요청값 오류로 본다
        findWork(request.workId());
    }

    /* 형벌표 6단계 (tier 1 → 6 순서) */
    public List<PenaltyDraftResponseDTO> generate(PenaltyDraftRequestDTO request) {

        Work work = findWork(request.workId());
        String input = PenaltyDraftPrompt.input(work, request);

        List<String> sentences = callAi(input);
        List<String> repeated = repeated(sentences, request.excludeSentences());

        // 이전 처분과 겹치거나 6단계 안에서 같은 처분이 나오면, 겹친 것을 짚어 한 번 더 요청한다
        if (!repeated.isEmpty()) {
            log.info("[PenaltyDraftService] 겹친 처분 {}개 → 한 번 더 요청 : {}", repeated.size(), repeated);
            sentences = callAi(input + PenaltyDraftPrompt.retryNote(repeated));
            repeated = repeated(sentences, request.excludeSentences());

            if (!repeated.isEmpty()) {
                log.warn("[PenaltyDraftService] 다시 요청해도 겹친 처분 : {}", repeated);
                throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
            }
        }

        return toPenalties(sentences);
    }

    // 처분 6개 → [ { tier, title, sentence } ] (tier 1 → 6 순서)
    private List<PenaltyDraftResponseDTO> toPenalties(List<String> sentences) {
        return IntStream.rangeClosed(1, PenaltyDraftPrompt.TIER_COUNT)
                .mapToObj(tier -> new PenaltyDraftResponseDTO(tier, ScoreRule.tierTitle(tier), sentences.get(tier - 1)))
                .toList();
    }

    /* AI 호출 → tier1 ~ tier6 처분 (실패는 OpenAiClient 의 AI_* 예외 그대로 · BaseWebSocketHandler 가 error 로 보낸다)
     *  - 하나라도 비면 쓸 수 없는 형벌표 → AI_UPSTREAM_ERROR
     *  - 50자 초과는 실패로 보지 않고 경고만 남긴다 (관리자가 고친 뒤 저장할 때 사건 등록 검사에 걸린다)
     * */
    private List<String> callAi(String input) {

        AiResult<JsonNode> response = openAiClient.createJson(
                PenaltyDraftPrompt.INSTRUCTIONS, input, MAX_OUTPUT_TOKENS, "penalty_draft", PenaltyDraftPrompt.SCHEMA);
        // TODO(연결) 8-4 response.usage() 를 ai_call_log 에 저장 (관리자 호출 : speaking_id · role = NULL)

        List<String> sentences = new ArrayList<>();
        for (int tier = 1; tier <= PenaltyDraftPrompt.TIER_COUNT; tier++) {
            String sentence = response.output().path("tier" + tier).asString("").strip();
            if (sentence.isEmpty()) {
                log.warn("[PenaltyDraftService] 빈 처분 : tier {}", tier);
                throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
            }
            if (sentence.length() > PenaltyDraftPrompt.SENTENCE_MAX_LENGTH) {
                log.warn("[PenaltyDraftService] 글자 수 초과 처분 : tier {}, {}자", tier, sentence.length());
            }
            sentences.add(sentence);
        }
        return sentences;
    }

    /* 겹친 처분 : 이전 처분과 같거나, 6단계 안에서 앞 단계와 같은 것
     *  띄어쓰기 · 문장부호만 다른 것도 같은 처분으로 본다 ("마법사 감옥 5년" = "마법사감옥 5년.")
     * */
    private List<String> repeated(List<String> sentences, List<String> excludeSentences) {

        Set<String> seen = new HashSet<>();
        excludeSentences.forEach(sentence -> seen.add(normalize(sentence)));

        List<String> repeated = new ArrayList<>();
        for (String sentence : sentences) {
            if (!seen.add(normalize(sentence))) {
                repeated.add(sentence);
            }
        }
        return repeated;
    }

    private String normalize(String sentence) {
        return sentence.replaceAll("[\\s\\p{P}]", "");
    }

    private Work findWork(int workId) {
        return workRepository.findActiveById(workId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WS_DRAFT_REQUEST_INVALID));
    }

    private boolean isInvalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }
}
