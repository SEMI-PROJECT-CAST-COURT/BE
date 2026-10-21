package com.verdict.castcourt.domain.workcase.ai.model;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

/* 실제 OpenAI 를 불러 보는 사건 초안 확인용 테스트 (커밋하지 않는다)
 *  환경 변수 OPENAI_API_KEY 가 있을 때만 실행되고, 없으면 건너뛴다. 실행할 때마다 토큰 비용이 든다.
 *  결과 · 글자 수 · 토큰 수 · 걸린 시간을 찍어 지시문과 출력 토큰 상한이 맞는지 눈으로 확인한다.
 * */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiCaseDraftLiveTest {

    private final OpenAiClient client = new OpenAiClient(JsonMapper.builder().build(),
            System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1",
            System.getenv().getOrDefault("OPENAI_MODEL", "gpt-6-luna"), "none");

    private final OpenAiCaseDraftAi ai = new OpenAiCaseDraftAi(client);

    private final Work work = new Work(new Category("영화", 1), "기생충", "봉준호", LocalDate.of(2019, 5, 30),
            "전원 백수인 기택네 가족이 부잣집 박 사장네에 차례로 위장 취업하며 벌어지는 이야기", "poster.png");

    @Test
    @DisplayName("쟁점 후보 → 다시 받기(앞 후보 제외) → 첫 후보로 개요 · 경과 다시 쓰기")
    void callRealAi() {

        // 1. 처음 받기
        AiResult<List<CaseDraftCandidateResponseDTO>> first = ai.candidates(work, List.of());
        print("처음 받기", first);

        // 2. 다시 받기 : 앞 후보의 쟁점을 뺀다
        List<String> exclude = first.output().stream().map(CaseDraftCandidateResponseDTO::question).toList();
        AiResult<List<CaseDraftCandidateResponseDTO>> second = ai.candidates(work, exclude);
        print("다시 받기", second);

        // 3. 첫 후보의 개요 · 경과를 고칠 점대로 다시 쓰기
        CaseDraftCandidateResponseDTO picked = first.output().get(0);
        CaseDraftRequestDTO request = new CaseDraftRequestDTO(picked.question(), picked.suspect(), picked.victim(),
                picked.summary(), picked.progress(), "경과를 더 짧게, 결말 스포일러는 줄여 줘");
        AiResult<CaseDraftRewriteResponseDTO> rewritten = ai.rewrite(work, request);

        System.out.println("\n===== 다시 쓰기 (고칠 점 : " + request.feedback() + ") =====");
        System.out.println("개요 (" + rewritten.output().summary().length() + "자) : " + rewritten.output().summary());
        System.out.println("경과 (" + rewritten.output().progress().length() + "자) : " + rewritten.output().progress());
        System.out.println("토큰 = " + rewritten.usage());
    }

    private void print(String title, AiResult<List<CaseDraftCandidateResponseDTO>> result) {
        System.out.println("\n===== " + title + " =====");
        for (CaseDraftCandidateResponseDTO candidate : result.output()) {
            System.out.println("쟁점 : " + candidate.question());
            System.out.println("  피의자 : " + candidate.suspect() + " / 피해자 : " + candidate.victim());
            System.out.println("  개요 (" + candidate.summary().length() + "자) : " + candidate.summary());
            System.out.println("  경과 (" + candidate.progress().length() + "자) : " + candidate.progress());
        }
        System.out.println("토큰 = " + result.usage());
    }
}
