package com.verdict.castcourt.domain.workcase.ai.prompt;

import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CaseDraftAiPrompt {

    // 최대 사건 개요 길이
    public static final int MAX_SUMMARY_LENGTH = 1000;
    // 최대 사건 경과 길이
    public static final int MAX_PROGRESS_LENGTH = 1000;
    // 최대 쟁점 길이
    public static final int MAX_QUESTION_LENGTH = 100;
    // 최대 피의자 이름 길이
    public static final int MAX_SUSPECT_LENGTH = 50;
    // 최대 피해자 이름 길이
    public static final int MAX_VICTIM_LENGTH = 50;

    public static final String CANDIDATES = """
            주어진 작품 정보로 토론할 만한 쟁점 3개와, 각 쟁점(question)의 피의자(suspect), 피해자(victim), 개요(summary), 경과(progress)를 만든다.
            사건 개요와 경과는 1000자를 넘어서는 안된다. 되도록 500자 내로 끝내면 좋다. 또한, 사건 쟁점을 생성할 때에는
            [제외할 쟁점] 존재하는 쟁점을 제외하고 생성해야 한다.
            쟁점은 100자를 넘어서는 안되고, 피의자/피해자는 50자를 넘어서는 안된다.
            """;
    public static final String REWRITE = """
            [고칠 점](feedback)을 반영하여 사건 개요와 경과만 다시 쓴다.
            입력으로는 [현재 초안]과 feedback을 준다.
            사건 개요와 경과는 1000자를 넘어서는 안된다.
            """;

    /* 쟁점 후보 하나의 모양 */
    private static final Map<String, Object> CANDIDATE_ITEM_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "question", Map.of("type", "string"),
                    "suspect", Map.of("type", "string"),
                    "victim", Map.of("type", "string"),
                    "summary", Map.of("type", "string"),
                    "progress", Map.of("type", "string")
            ),
            "required", List.of("question", "suspect", "victim", "summary", "progress"),
            "additionalProperties", false
    );

    /* 쟁점 후보 응답 모양 : strict 스키마는 맨 바깥이 object 여야 해서 배열을 candidates 로 감싼다
     *  개수(3개) · 글자 수는 서비스에서 검사한다
     * */
    public static final Map<String, Object> CANDIDATE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "candidates", Map.of(
                            "type", "array",
                            "items", CANDIDATE_ITEM_SCHEMA
                    )
            ),
            "required", List.of("candidates"),
            "additionalProperties", false
    );

    /* 개요 · 경과 다시 쓰기 응답 모양 */
    public static final Map<String, Object> REWRITE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "progress", Map.of("type", "string")
            ),
            "required", List.of("summary", "progress"),
            "additionalProperties", false
    );

    /* 쟁점 후보 입력 : 작품 정보 + 제외할 쟁점 (다시 받기일 때만 = 쟁점, 개요, 경과 다 다시 씀) */
    public static String candidatesInput(Work work, List<String> excludeQuestions) {

        String excluded = excludeQuestions.isEmpty()
                ? "없음"
                : excludeQuestions.stream()
                        .map(question -> "- " + question)
                        .collect(Collectors.joining("\n"));

        return workText(work)
                + "[제외할 쟁점]\n" + excluded + "\n";
    }

    /* 다시 쓰기 입력 : 작품 정보 + 현재 초안 + 고칠 점 (개요와 경과만 다시 씀) */
    public static String rewriteInput(Work work, CaseDraftRequestDTO request) {

        return workText(work)
                + "[현재 초안]\n"
                + "쟁점 : " + request.question() + "\n"
                + "피의자 : " + request.suspect() + "\n"
                + "피해자 : " + request.victim() + "\n"
                + "개요 : " + request.summary() + "\n"
                + "경과 : " + request.progress() + "\n\n"
                + "[고칠 점]\n" + request.feedback() + "\n";
    }

    /* 작품 정보 (작품 소개는 비어 있을 수 있다) */
    private static String workText(Work work) {

        String summary = work.getSummary() == null || work.getSummary().isBlank()
                ? "없음"
                : work.getSummary();

        return "[작품]\n"
                + "제목 : " + work.getTitle() + "\n"
                + "원작자 : " + work.getAuthor() + "\n"
                + "개봉일 : " + work.getReleasedOn() + "\n"
                + "분류 : " + work.getCategory().getName() + "\n"
                + "소개 : " + summary + "\n\n";
    }
}
