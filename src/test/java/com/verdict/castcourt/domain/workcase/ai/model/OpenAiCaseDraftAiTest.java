package com.verdict.castcourt.domain.workcase.ai.model;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.ai.OpenAiClient;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 사건 초안 AI : OpenAiClient 는 가짜(@Mock)로 두고, 프롬프트 입력과 응답 해석만 확인한다 */
@ExtendWith(MockitoExtension.class)
class OpenAiCaseDraftAiTest {

    private static final AiUsage USAGE = new AiUsage("test-model", 100, 10, LocalDateTime.now(), 5);

    // 작품 소개(summary)는 비워 둔다 → 입력에 '소개 : 없음'
    private static final Work WORK = new Work(new Category("영화", 1), "기생충", "봉준호",
            LocalDate.of(2019, 5, 30), null, "poster.png");

    @Mock
    private OpenAiClient openAiClient;

    @Test
    @DisplayName("쟁점 후보 : 후보 3개를 DTO 로 바꾸고 사용량을 그대로 넘긴다")
    void candidates() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "피의자1", "피해자1", "개요1", "경과1"),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));

        AiResult<List<CaseDraftCandidateResponseDTO>> result = ai().candidates(WORK, List.of());

        assertEquals(3, result.output().size());
        assertEquals(new CaseDraftCandidateResponseDTO("쟁점1", "피의자1", "피해자1", "개요1", "경과1"),
                result.output().get(0));
        assertSame(USAGE, result.usage());
    }

    @Test
    @DisplayName("쟁점 후보 입력 : 작품 정보와 제외할 쟁점을 넣는다")
    void candidatesInputWithExclude() {
        givenThreeCandidates();

        ai().candidates(WORK, List.of("A 는 유죄인가?", "B 는 유죄인가?"));

        String input = captureInput("case_draft_candidates");
        assertTrue(input.contains("제목 : 기생충"));
        assertTrue(input.contains("원작자 : 봉준호"));
        assertTrue(input.contains("개봉일 : 2019-05-30"));
        assertTrue(input.contains("분류 : 영화"));
        assertTrue(input.contains("소개 : 없음"));
        assertTrue(input.contains("[제외할 쟁점]\n- A 는 유죄인가?\n- B 는 유죄인가?"));
    }

    @Test
    @DisplayName("쟁점 후보 입력 : 처음 받기면 제외할 쟁점은 '없음'")
    void candidatesInputWithoutExclude() {
        givenThreeCandidates();

        ai().candidates(WORK, List.of());

        assertTrue(captureInput("case_draft_candidates").contains("[제외할 쟁점]\n없음"));
    }

    @Test
    @DisplayName("쟁점 후보 : 3개가 아니면 AI_UPSTREAM_ERROR")
    void candidatesWrongCount() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "피의자1", "피해자1", "개요1", "경과1"),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2")));

        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().candidates(WORK, List.of()));
    }

    @Test
    @DisplayName("쟁점 후보 : candidates 가 없으면 AI_UPSTREAM_ERROR")
    void candidatesMissing() {
        givenJson("{}");

        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().candidates(WORK, List.of()));
    }

    @Test
    @DisplayName("쟁점 후보 : 빈 필드가 있으면 AI_UPSTREAM_ERROR")
    void candidatesBlankField() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "  ", "피해자1", "개요1", "경과1"),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));

        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().candidates(WORK, List.of()));
    }

    @Test
    @DisplayName("쟁점 후보 : 피의자 51자면 AI_UPSTREAM_ERROR")
    void candidatesSuspectTooLong() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "가".repeat(51), "피해자1", "개요1", "경과1"),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));

        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().candidates(WORK, List.of()));
    }

    @Test
    @DisplayName("쟁점 후보 : 경과 1000자는 통과, 1001자면 AI_UPSTREAM_ERROR")
    void candidatesProgressLength() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "피의자1", "피해자1", "개요1", "가".repeat(1000)),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));
        assertEquals(1000, ai().candidates(WORK, List.of()).output().get(0).progress().length());

        givenJson(candidatesJson(
                candidateJson("쟁점1", "피의자1", "피해자1", "개요1", "가".repeat(1001)),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));
        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().candidates(WORK, List.of()));
    }

    @Test
    @DisplayName("다시 쓰기 : 개요 · 경과를 DTO 로 바꾸고, 입력에 현재 초안과 고칠 점을 넣는다")
    void rewrite() {
        givenJson("{\"summary\":\"새 개요\",\"progress\":\"새 경과\"}");
        CaseDraftRequestDTO request = new CaseDraftRequestDTO(
                "쟁점", "피의자", "피해자", "옛 개요", "옛 경과", "경과를 더 짧게");

        AiResult<CaseDraftRewriteResponseDTO> result = ai().rewrite(WORK, request);

        assertEquals(new CaseDraftRewriteResponseDTO("새 개요", "새 경과"), result.output());
        assertSame(USAGE, result.usage());

        String input = captureInput("case_draft_rewrite");
        assertTrue(input.contains("[현재 초안]\n쟁점 : 쟁점\n피의자 : 피의자\n피해자 : 피해자\n개요 : 옛 개요\n경과 : 옛 경과"));
        assertTrue(input.contains("[고칠 점]\n경과를 더 짧게"));
    }

    @Test
    @DisplayName("다시 쓰기 : 필드가 빠지면 AI_UPSTREAM_ERROR")
    void rewriteMissingField() {
        givenJson("{\"summary\":\"새 개요\"}");
        CaseDraftRequestDTO request = new CaseDraftRequestDTO(
                "쟁점", "피의자", "피해자", "옛 개요", "옛 경과", "경과를 더 짧게");

        assertErrorCode(ErrorCode.AI_UPSTREAM_ERROR, () -> ai().rewrite(WORK, request));
    }

    private OpenAiCaseDraftAi ai() {
        return new OpenAiCaseDraftAi(openAiClient);
    }

    private void givenThreeCandidates() {
        givenJson(candidatesJson(
                candidateJson("쟁점1", "피의자1", "피해자1", "개요1", "경과1"),
                candidateJson("쟁점2", "피의자2", "피해자2", "개요2", "경과2"),
                candidateJson("쟁점3", "피의자3", "피해자3", "개요3", "경과3")));
    }

    private void givenJson(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        when(openAiClient.createJson(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new AiResult<>(node, USAGE));
    }

    /* createJson 에 넘긴 입력(input) */
    private String captureInput(String schemaName) {
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).createJson(anyString(), input.capture(), anyInt(), eq(schemaName), any());
        return input.getValue();
    }

    private static String candidatesJson(String... candidates) {
        return "{\"candidates\":[" + String.join(",", candidates) + "]}";
    }

    private static String candidateJson(String question, String suspect, String victim,
                                        String summary, String progress) {
        return """
                {"question":"%s","suspect":"%s","victim":"%s","summary":"%s","progress":"%s"}
                """.formatted(question, suspect, victim, summary, progress);
    }

    private static void assertErrorCode(ErrorCode expected, Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        assertEquals(expected, e.getErrorCode());
    }
}
