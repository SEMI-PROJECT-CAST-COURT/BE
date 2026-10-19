package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.ai.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.JudgeResult;
import com.verdict.castcourt.domain.speaking.ai.OpponentAi;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/* 발언 제출 (SPK-01) : 내 발언 판정 → AI 상대 발언 → 상대 발언 판정 → 단계 알림을 SSE 로 흘려보낸다
 *  - 요청 스레드 : 검사(400 · 403 · 404)만 하고 SseEmitter 를 돌려준다. 여기서 난 오류는 평소처럼 JSON 오류 응답이 된다.
 *  - 작업 스레드 : AI 를 부르며 이벤트를 보낸다. 여기서 난 오류는 error 이벤트로 알린다. (이미 응답 중이라 상태 코드를 못 바꾼다)
 *  흐름은 예제 RecommendationService 와 같다.
 * */
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

    /* 발언 제출 : 검사 → 작업 스레드에 턴 처리를 맡기고 응답 통로를 돌려준다 */
    public SseEmitter submit(int userId, String trialId, SpeakingRequestDTO request) {

        String content = request == null ? null : request.content();
        TrialSession session = checkSubmittable(userId, trialId, content);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        taskExecutor.execute(() -> {
            runTurn(session, content, (event, data) -> send(emitter, event, data));
            emitter.complete();     // 응답 끝
        });
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

    /* 한 턴 처리 (작업 스레드) : 판정 · 상대 발언을 재판에 바로 기록하고 turn_done 으로 끝낸다
     *  AI 가 실패했을 때의 처리는 6-4 에서 붙인다.
     * */
    void runTurn(TrialSession session, String content, BiConsumer<String, Object> sender) {

        playTurn(session, content, sender);

        String nextTurn = session.isFinished() ? SpeakingTurnDoneEventDTO.VERDICT : SpeakingTurnDoneEventDTO.USER;
        sender.accept(EVENT_TURN_DONE, new SpeakingTurnDoneEventDTO(nextTurn));
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

    /* SSE 이벤트 하나 보내기 : event: 이름 / data: JSON */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);      // 브라우저가 끊은 경우 · 6-5 에서 따로 처리한다
        }
    }
}