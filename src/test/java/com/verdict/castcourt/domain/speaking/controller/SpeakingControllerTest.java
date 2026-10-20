package com.verdict.castcourt.domain.speaking.controller;

import com.verdict.castcourt.domain.speaking.ai.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.OpponentAi;
import com.verdict.castcourt.domain.speaking.service.SpeakingService;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* 발언 제출 컨트롤러 : 서버 전체를 띄우지 않고 컨트롤러 + 전역 예외 처리만으로 HTTP 요청을 흉내 낸다 (MockMvc) */
class SpeakingControllerTest {

    private static final AiUsage USAGE = new AiUsage("test-model", 0, 0, LocalDateTime.now(), 0);

    private final TrialSessionStore store = new TrialSessionStore();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // AI 인터페이스는 메서드가 하나라 람다로 바로 만든다 (판사는 ①~④ 모두 충족, 상대는 두 조각으로 대사를 보낸다)
        JudgeAi judgeAi = (session, content) -> new AiResult<>(JudgeResult.of(session, true, true, true, true, 10), USAGE);
        OpponentAi opponentAi = (session, onDelta) -> {
            onDelta.accept("재판장님, ");
            onDelta.accept("상대 측입니다.");
            return new AiResult<>("재판장님, 상대 측입니다.", USAGE);
        };
        SpeakingService service = new SpeakingService(store, judgeAi, opponentAi, Runnable::run);
        mockMvc = MockMvcBuilders.standaloneSetup(new SpeakingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("SSE 를 열기 전 오류는 Accept: text/event-stream 이어도 JSON 오류 응답 (404)")
    void errorBeforeStream() throws Exception {
        mockMvc.perform(post("/api/trials/없는-id/speakings")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"발언\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.businessCode").value("TRIAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 제출 : text/event-stream 으로 judge ~ turn_done 이벤트가 온다")
    void stream() throws Exception {
        store.save(new TrialSession("trial-1", 4, 7, Side.PROSECUTION, TrialMode.CHICKEN, snapshot(), List.of(11, 12)));

        MvcResult result = mockMvc.perform(post("/api/trials/trial-1/speakings")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"재판장님, 모두 진술입니다.\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:judge")))
                .andExpect(content().string(containsString("event:opponent_delta")))
                .andExpect(content().string(containsString("event:turn_done")));
    }

    private CaseSnapshot snapshot() {
        return new CaseSnapshot(7, 1, "작품", "쟁점", "피의자", "피해자", "개요", "경과",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "카드 1", "내용", 8),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "카드 2", "내용", 12)),
                List.of());
    }
}
