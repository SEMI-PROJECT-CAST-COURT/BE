package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.ai.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.OpponentAi;
import com.verdict.castcourt.domain.speaking.dto.SpeakingErrorEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingJudgeEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingRequestDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingStageEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingTextEventDTO;
import com.verdict.castcourt.domain.speaking.dto.SpeakingTurnDoneEventDTO;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionSpeaking;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/* 발언 제출 (SPK-01) : 내 발언 판정 → AI 상대 발언 → 상대 발언 판정 → 단계 알림을 SSE 로 흘려보낸다
 *  - 요청 스레드 : 검사(400 · 403 · 404)만 하고 SseEmitter 를 돌려준다. 여기서 난 오류는 평소처럼 JSON 오류 응답이 된다.
 *  - 작업 스레드 : AI 를 부르며 이벤트를 보낸다. 여기서 난 오류는 error 이벤트로 알린다. (이미 응답 중이라 상태 코드를 못 바꾼다)
 *  흐름은 예제 RecommendationService 와 같다.
 * */
@Slf4j
@Service
public class SpeakingService {

    public static final int MAX_CONTENT_LENGTH = 300;
    // 한 턴에 AI 를 세 번 부른다 (판정 · 상대 발언 · 판정). OpenAI 응답 대기 60초 x 3 을 덮도록 넉넉히 잡는다.
    private static final long SSE_TIMEOUT = 180_000L;

    // SSE 이벤트 이름 (명세 v6)
    static final String EVENT_JUDGE = "judge";
    static final String EVENT_OPPONENT_DELTA = "opponent_delta";
    static final String EVENT_OPPONENT_JUDGE = "opponent_judge";
    static final String EVENT_STAGE = "stage";
    static final String EVENT_TURN_DONE = "turn_done";
    static final String EVENT_ERROR = "error";

    private final TrialSessionStore trialSessionStore;
    private final JudgeAi judgeAi;              // 가짜 / 실제 중 설정값으로 정해진 하나 (5장)
    private final OpponentAi opponentAi;
    private final TaskExecutor taskExecutor;

    public SpeakingService(TrialSessionStore trialSessionStore, JudgeAi judgeAi, OpponentAi opponentAi,
                           // 스프링 부트가 만들어 둔 비동기 작업용 실행기 (가상 스레드를 켜면 가상 스레드로 실행)
                           @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.trialSessionStore = trialSessionStore;
        this.judgeAi = judgeAi;
        this.opponentAi = opponentAi;
        this.taskExecutor = taskExecutor;
    }

    /* 발언 제출 : 검사 → 처리 중 표시 → 작업 스레드에 턴 처리를 맡기고 응답 통로를 돌려준다 */
    public SseEmitter submit(int userId, String trialId, SpeakingRequestDTO request) {

        String content = request == null ? null : request.content();
        TrialSession session = checkSubmittable(userId, trialId, content);

        // 같은 재판에 발언이 동시에 두 번 들어오면 두 번째는 막는다 (처리 중에는 내 차례가 아닌 것으로 본다)
        if (!session.tryStartTurn()) {
            throw new BusinessException(ErrorCode.SPEAKING_NOT_YOUR_TURN);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 브라우저가 끊었는지 기억하는 깃발 (여러 스레드가 함께 봐서 AtomicBoolean)
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));

        try {
            taskExecutor.execute(() -> {
                runTurn(session, content, (event, data) -> send(emitter, closed, event, data));
                if (!closed.get()) {
                    emitter.complete();     // 응답 끝 (브라우저가 이미 끊었으면 닫을 것이 없다)
                }
            });
        } catch (RuntimeException e) {
            // 작업을 맡기지 못했으면 처리 중 표시를 풀어 다음 제출을 막지 않는다
            session.endTurn();
            throw e;
        }
        return emitter;
    }

    /* SSE 를 열기 전 검사 : 404 → 403 → 내 차례 → 글자 수 */
    TrialSession checkSubmittable(int userId, String trialId, String content) {

        TrialSession session = trialSessionStore.findByTrialId(trialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIAL_NOT_FOUND));

        if (session.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TRIAL_ACCESS_DENIED);
        }

        // 발언이 다 끝났거나(null) AI 차례면 사용자가 말할 수 없다
        TrialTurn turn = session.currentTurn();
        if (turn == null || turn.speaker() != Speaker.USER) {
            throw new BusinessException(ErrorCode.SPEAKING_NOT_YOUR_TURN);
        }

        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.SPEAKING_CONTENT_INVALID);
        }
        return session;
    }

    /* 한 턴 처리 (작업 스레드) : 복사본에서 진행하고, 끝까지 성공했을 때만 원본에 반영한다
     *  어떤 경우에도 예외를 밖으로 던지지 않는다. 실패는 error 이벤트로 보낸다.
     * */
    void runTurn(TrialSession session, String content, BiConsumer<String, Object> sender) {

        try {
            TrialSession draft = session.copy();
            playTurn(draft, content, sender);

            // 성공 → 반영 · 실패 횟수 초기화 · 처리 중 해제를 turn_done 보다 먼저 한다
            // (turn_done 을 받자마자 다음 발언을 보내도 막히지 않게)
            session.applyFrom(draft);
            session.resetFailCount();
            session.endTurn();

            String nextTurn = draft.isFinished() ? SpeakingTurnDoneEventDTO.VERDICT : SpeakingTurnDoneEventDTO.USER;
            sender.accept(EVENT_TURN_DONE, new SpeakingTurnDoneEventDTO(nextTurn));

        } catch (ClientClosedException e) {
            // 브라우저가 먼저 끊었다 → 반영하지 않고(복사본을 버림) 실패로도 세지 않는다
            log.info("[SpeakingService] 브라우저 연결 끊김 : trialId={}", session.getTrialId());

        } catch (RuntimeException e) {
            ErrorCode errorCode = e instanceof BusinessException businessException
                    ? businessException.getErrorCode()
                    : ErrorCode.INTERNAL_SERVER_ERROR;
            if (!(e instanceof BusinessException)) {
                log.error("[SpeakingService] 턴 처리 중 예상하지 못한 오류", e);
            }

            int failCount = session.increaseFailCount();
            session.endTurn();
            try {
                sender.accept(EVENT_ERROR, SpeakingErrorEventDTO.of(errorCode, failCount));
            } catch (ClientClosedException ignored) {
                // 오류를 알리려는 사이에 브라우저가 끊었다
            }

        } finally {
            session.endTurn();      // 위에서 못 풀고 끝난 경우를 위한 안전장치
        }
    }

    /* 판정 → (끝나지 않았으면) 상대 발언 → 판정 → 단계가 바뀌었으면 stage */
    private void playTurn(TrialSession session, String content, BiConsumer<String, Object> sender) {

        TrialTurn before = session.currentTurn();

        // 1. 내 발언 판정
        TrialSessionSpeaking mine = judgeAndRecord(session, content);
        sender.accept(EVENT_JUDGE, SpeakingJudgeEventDTO.of(mine, session.getMode()));

        // 2. AI 상대 발언 (내 발언이 마지막이면 없다) : 글자 조각이 올 때마다 바로 보낸다
        if (!session.isFinished()) {
            String opponentContent = speakOpponent(session,
                    delta -> sender.accept(EVENT_OPPONENT_DELTA, new SpeakingTextEventDTO(delta)));

            // 3. 상대 발언 판정 (사용자와 같은 기준)
            TrialSessionSpeaking theirs = judgeAndRecord(session, opponentContent);
            sender.accept(EVENT_OPPONENT_JUDGE, SpeakingJudgeEventDTO.of(theirs, session.getMode()));
        }

        // 4. 다음 내 차례의 단계 · 카드가 바뀌었으면 알린다 (다 끝났으면 turn_done 의 VERDICT 로 알린다)
        TrialTurn after = session.currentTurn();
        if (after != null && (after.stage() != before.stage() || !Objects.equals(after.cardId(), before.cardId()))) {
            sender.accept(EVENT_STAGE, SpeakingStageEventDTO.of(after));
        }
    }

    // 판사 AI 로 판정하고 기록한다 // TODO(연결) 8-4 judge 결과의 usage 를 ai_call_log 에 저장 (role = JUDGE)
    private TrialSessionSpeaking judgeAndRecord(TrialSession session, String content) {

        JudgeResult judged = callAi(() -> judgeAi.judge(session, content).output(), ErrorCode.AI_JUDGE_FAILED);
        return session.record(content, judged.issue(), judged.evidence(), judged.rebut(), judged.novel(),
                judged.weight());
    }

    // 상대 AI 발언 // TODO(연결) 8-4 usage 를 ai_call_log 에 저장 (role = PROSECUTION / DEFENSE)
    private String speakOpponent(TrialSession session, Consumer<String> onDelta) {
        return callAi(() -> opponentAi.speak(session, onDelta).output(), ErrorCode.AI_OPPONENT_FAILED);
    }

    /* AI 호출 : OpenAiClient 의 일반 실패(AI_UPSTREAM_ERROR)를 명세의 코드(판정 실패 / 상대 발언 실패)로 바꾼다
     *  설정 문제(503) · 시간 초과(504)는 명세에도 따로 있어서 그대로 둔다.
     * */
    private <T> T callAi(Supplier<T> call, ErrorCode failCode) {
        try {
            return call.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.AI_UPSTREAM_ERROR) {
                throw new BusinessException(failCode);
            }
            throw e;
        }
    }

    /* SSE 이벤트 하나 보내기 : event: 이름 / data: JSON
     *  브라우저가 끊었으면 send() 가 실패한다 → ClientClosedException 으로 바꿔 턴 처리를 멈춘다.
     *  상대 발언 글자 조각을 보내다 끊기면 OpenAI 스트림 읽기도 함께 멈춰 비용을 아낀다.
     * */
    private void send(SseEmitter emitter, AtomicBoolean closed, String event, Object data) {

        if (closed.get()) {
            throw new ClientClosedException();
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            closed.set(true);
            throw new ClientClosedException();
        }
    }

    /* 브라우저가 연결을 끊었음을 알리는 내부용 예외 (테스트에서 끊김을 흉내 내려고 패키지 안에서 보이게 둔다) */
    static class ClientClosedException extends UncheckedIOException {
        ClientClosedException() {
            super(new IOException("브라우저가 연결을 끊었다"));
        }
    }
}