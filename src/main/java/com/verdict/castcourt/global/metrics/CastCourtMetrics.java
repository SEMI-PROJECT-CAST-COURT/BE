package com.verdict.castcourt.global.metrics;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/* Cast Court 전용 지표 : /actuator/prometheus 로 나가서 프로메테우스가 수집한다 (monitoring/ 의 대시보드 · 알림이 쓴다)
 *  기본 지표(HTTP · JVM · DB 커넥션 풀 · 스레드 풀)만으로는 이 프로젝트의 핵심인 AI 가 보이지 않는다.
 *  - 발언 제출은 SSE 라서 AI 가 실패해도 HTTP 상태는 200 이다. 실패는 error 이벤트로만 나간다.
 *  - 관리자 초안(사건 · 카드 · 형벌표)은 WebSocket 이라 HTTP 지표에 아예 잡히지 않는다.
 *  그래서 모든 AI 호출이 거치는 OpenAiClient, 발언 턴(SpeakingService), 재판 시작 · 판결(TrialService)에서 직접 센다.
 *
 *  프로메테우스 이름                    내용                    태그
 *   castcourt_ai_requests_seconds_*   AI 호출 시간 · 횟수      purpose(용도), outcome(SUCCESS 또는 실패 코드)
 *   castcourt_ai_tokens_total         AI 토큰 사용량          purpose, type(input · output)
 *   castcourt_speaking_turns_total    발언 턴 처리 결과        outcome
 *   castcourt_trial_started_total     재판 시작               role, mode
 *   castcourt_trial_verdicts_total    판결                   role, mode, penalty_tier(1~6), outcome(WIN · LOSE)
 *   castcourt_trial_active            진행 중 재판 수          -
 *  (모든 지표에 application="cast-court" 태그가 붙는다 · application.yaml management.metrics.tags)
 *
 *  기록은 static 메서드로 한다 (Metrics.globalRegistry)
 *   스프링 부트가 프로메테우스 레지스트리를 전역 레지스트리에도 넣어 준다 (management.metrics.use-global-registry 기본값 true).
 *   그래서 OpenAiClient · TrialService 등의 생성자를 바꾸지 않아도 되고, new 로 만드는 기존 테스트도 그대로 돈다.
 *   스프링 없이 도는 단위 테스트에서는 전역 레지스트리가 비어 있어서 기록이 그냥 버려진다.
 *
 *  0 으로 미리 등록하는 이유 (bindTo)
 *   카운터는 처음 기록될 때 생기고, 생기자마자 값이 1 이다.
 *   increase() · rate() 는 두 시점의 차이로 계산하므로 0 → 1 을 보지 못해 첫 번째 사건을 놓친다.
 *   (서버를 켜고 처음 난 AI 실패에 알림이 울리지 않는 식)
 *   알고 있는 태그 조합을 서버가 뜰 때 0 으로 만들어 두면 첫 사건부터 증가로 잡힌다.
 * */
@Component
public class CastCourtMetrics implements MeterBinder {

    // ==================== 태그 값 ====================
    public static final String SUCCESS = "SUCCESS";
    // 스트리밍 도중 브라우저 · WebSocket 연결이 끊겨 멈춘 AI 호출 (실패가 아니라 중단)
    public static final String CANCELLED = "CANCELLED";
    // 브라우저가 먼저 끊은 발언 턴 (SpeakingService 는 반영하지 않고 실패 횟수로도 세지 않는다)
    public static final String CLIENT_CLOSED = "CLIENT_CLOSED";
    // 스트리밍(streamText)은 지금 상대 발언만 쓴다. 나머지 용도는 createJson 의 schemaName 을 그대로 쓴다
    public static final String PURPOSE_OPPONENT_SPEECH = "opponent_speech";

    // ==================== 0 으로 미리 만들어 둘 태그 조합 ====================
    // AI 용도 = createJson 의 schemaName. 새 AI 호출을 추가하면 여기에도 넣는다 (빠뜨려도 기록은 되지만 첫 사건을 놓친다)
    private static final List<String> AI_PURPOSES = List.of(
            "trial_topics",             // 재판 시작 : 공방 논점 · 변호인 대응 카드 (OpenAiTopicAi)
            "judge_result",             // 발언 판정 (JudgeAi)
            PURPOSE_OPPONENT_SPEECH,    // 상대 발언 · 스트리밍 (OpponentAi)
            "verdict_summary",          // 판사 총평 (VerdictAi)
            "case_draft_candidates",    // 관리자 : 사건 초안 (OpenAiCaseDraftAi)
            "case_draft_rewrite",       // 관리자 : 사건 개요 · 경과 다시 쓰기 (OpenAiCaseDraftAi)
            "card_draft",               // 관리자 : 근거 카드 초안 (CardDraftService)
            "card_suggest",             // 관리자 : 근거 카드 수정 제안 (CardDraftService)
            "penalty_draft",            // 관리자 : 형벌표 초안 (PenaltyDraftService)
            "work_summary_draft"        // 관리자 : 작품 소개 초안 (WorkSummaryDraftService)
    );
    // OpenAiClient 가 던지는 실패 코드 + 중단 + 예상하지 못한 오류
    private static final List<String> AI_OUTCOMES = List.of(
            SUCCESS,
            ErrorCode.AI_UPSTREAM_ERROR.name(),
            ErrorCode.AI_TIMEOUT.name(),
            ErrorCode.AI_CONFIGURATION_MISSING.name(),
            CANCELLED,
            ErrorCode.INTERNAL_SERVER_ERROR.name()
    );
    // SpeakingService.runTurn 이 error 이벤트로 보내는 코드 + 성공 + 끊김
    private static final List<String> TURN_OUTCOMES = List.of(
            SUCCESS,
            CLIENT_CLOSED,
            ErrorCode.AI_JUDGE_FAILED.name(),
            ErrorCode.AI_OPPONENT_FAILED.name(),
            ErrorCode.AI_TIMEOUT.name(),
            ErrorCode.AI_CONFIGURATION_MISSING.name(),
            ErrorCode.INTERNAL_SERVER_ERROR.name()
    );

    private final TrialSessionStore trialSessionStore;

    public CastCourtMetrics(TrialSessionStore trialSessionStore) {
        this.trialSessionStore = trialSessionStore;
    }

    /* 서버가 뜰 때 스프링 부트가 한 번 부른다 (MeterBinder 빈은 자동으로 레지스트리에 연결된다) */
    @Override
    public void bindTo(MeterRegistry registry) {

        // 진행 중 재판 수 : 프로메테우스가 수집할 때마다 보관소를 세어 답한다
        Gauge.builder("castcourt.trial.active", trialSessionStore, TrialSessionStore::activeCount)
                .description("진행 중인 재판 수 (30분 넘게 활동이 없는 판은 빼고 센다)")
                .register(registry);

        for (String purpose : AI_PURPOSES) {
            for (String outcome : AI_OUTCOMES) {
                aiRequestTimer(registry, purpose, outcome);
            }
            aiTokenCounter(registry, purpose, "input");
            aiTokenCounter(registry, purpose, "output");
        }

        for (String outcome : TURN_OUTCOMES) {
            speakingTurnCounter(registry, outcome);
        }

        // 형벌 단계 1~6 (게이지 0 → 1단계, 100 → 6단계)
        int minTier = ScoreRule.penaltyTier(ScoreRule.MIN_SCORE);
        int maxTier = ScoreRule.penaltyTier(ScoreRule.MAX_SCORE);
        for (Side role : Side.values()) {
            for (TrialMode mode : TrialMode.values()) {
                trialStartedCounter(registry, role, mode);
                for (int tier = minTier; tier <= maxTier; tier++) {
                    verdictCounter(registry, role, mode, tier);
                }
            }
        }
    }

    // ==================== 기록 (어디서든 static 으로 부른다) ====================

    /* AI 호출 한 번 : 걸린 시간 + 결과 (OpenAiClient) */
    public static void recordAiRequest(String purpose, String outcome, long elapsedNanos) {
        aiRequestTimer(Metrics.globalRegistry, purpose, outcome).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /* AI 토큰 사용량 · 토큰 수가 곧 비용이다 (OpenAiClient · 성공한 호출만) */
    public static void recordAiTokens(String purpose, int inputTokens, int outputTokens) {
        aiTokenCounter(Metrics.globalRegistry, purpose, "input").increment(inputTokens);
        aiTokenCounter(Metrics.globalRegistry, purpose, "output").increment(outputTokens);
    }

    /* 발언 턴 하나의 결과 (SpeakingService) */
    public static void recordSpeakingTurn(String outcome) {
        speakingTurnCounter(Metrics.globalRegistry, outcome).increment();
    }

    /* 재판 시작 (TrialService) */
    public static void recordTrialStarted(Side role, TrialMode mode) {
        trialStartedCounter(Metrics.globalRegistry, role, mode).increment();
    }

    /* 판결 저장 (TrialService) */
    public static void recordVerdict(Side role, TrialMode mode, int penaltyTier) {
        verdictCounter(Metrics.globalRegistry, role, mode, penaltyTier).increment();
    }

    // ==================== 지표 모양 (미리 등록과 기록이 같은 모양을 쓰도록 한곳에 둔다) ====================

    private static Timer aiRequestTimer(MeterRegistry registry, String purpose, String outcome) {
        return Timer.builder("castcourt.ai.requests")
                .description("OpenAI 호출 시간 (스트리밍은 마지막 글자까지)")
                .tag("purpose", purpose)
                .tag("outcome", outcome)
                // AI 호출은 수 초 ~ 수십 초라 구간을 직접 정한다 (OpenAiClient 응답 대기 상한 60초)
                .serviceLevelObjectives(seconds(1), seconds(2), seconds(3), seconds(5),
                        seconds(10), seconds(20), seconds(30), seconds(60))
                .register(registry);
    }

    private static Counter aiTokenCounter(MeterRegistry registry, String purpose, String type) {
        return Counter.builder("castcourt.ai.tokens")
                .description("OpenAI 토큰 사용량 (type = input 보낸 토큰 · output 받은 토큰)")
                .tag("purpose", purpose)
                .tag("type", type)
                .register(registry);
    }

    private static Counter speakingTurnCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("castcourt.speaking.turns")
                .description("발언 턴 처리 결과 (SSE 라서 실패해도 HTTP 상태는 200)")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static Counter trialStartedCounter(MeterRegistry registry, Side role, TrialMode mode) {
        return Counter.builder("castcourt.trial.started")
                .description("시작된 재판 수")
                .tag("role", role.name())
                .tag("mode", mode.name())
                .register(registry);
    }

    private static Counter verdictCounter(MeterRegistry registry, Side role, TrialMode mode, int penaltyTier) {
        return Counter.builder("castcourt.trial.verdicts")
                .description("판결 수 (outcome 은 사용자 기준 승패)")
                .tag("role", role.name())
                .tag("mode", mode.name())
                .tag("penalty_tier", String.valueOf(penaltyTier))
                .tag("outcome", ScoreRule.isWin(role, penaltyTier) ? "WIN" : "LOSE")
                .register(registry);
    }

    private static Duration seconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }
}
