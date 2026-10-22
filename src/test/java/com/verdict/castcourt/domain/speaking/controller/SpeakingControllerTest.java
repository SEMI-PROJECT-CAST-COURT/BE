package com.verdict.castcourt.domain.speaking.controller;

import com.verdict.castcourt.domain.speaking.ai.model.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.dto.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.speaking.service.SpeakingService;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.handler.GlobalExceptionHandler;
import com.verdict.castcourt.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
        // 가짜 AI (판사는 ①~④ 모두 충족, 상대는 두 조각으로 대사를 보낸다)
        JudgeAi judgeAi = mock(JudgeAi.class);
        when(judgeAi.judge(any(), any())).thenAnswer(call ->
                new AiResult<>(JudgeResult.of(call.getArgument(0), true, true, true, true, 10), USAGE));
        OpponentAi opponentAi = mock(OpponentAi.class);
        when(opponentAi.speak(any(), any())).thenAnswer(call -> {
            Consumer<String> onDelta = call.getArgument(1);
            onDelta.accept("재판장님, ");
            onDelta.accept("상대 측입니다.");
            return new AiResult<>("재판장님, 상대 측입니다.", USAGE);
        });
        SpeakingService service = new SpeakingService(store, judgeAi, opponentAi, Runnable::run);
        // 로그인 사용자 4 (JWT 필터가 하는 일을 대신한다) · @AuthenticationPrincipal 이 이 값을 꺼낸다
        CustomUserDetails user = new CustomUserDetails(4, "user01", null, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new SpeakingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
