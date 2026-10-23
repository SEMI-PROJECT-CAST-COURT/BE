package com.verdict.castcourt.global.infra.log.ai.total;

import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.infra.log.ai.AiFeature;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/* AI 사용량을 묶음(재판 한 판 · 사건 하나 · 작품 하나)별로 모아 두는 곳
 *  - 묶음 key : "TRIAL:{trialId}" · "CASE:workId={workId}" · "WORK:adminId={관리자 id}"
 *  - AI 를 부르는 메서드가 도는 동안 지금 묶음(Scope)을 스레드에 걸어 둔다 → AiUsageAspect 가 그 묶음에 더한다
 *  - 묶음이 끝나는 곳(판결 · 사건 등록 · 작품 등록)에서 take 로 꺼내 합계를 찍는다
 *  - 끝나지 않고 버려진 묶음은 다음 AI 호출 때 합계를 찍고 지운다 (재판 30분, 작품 · 사건은 작성이 길어서 2시간)
 * */
@Slf4j
@Component
public class AiUsageScopes {

    public static final String TRIAL = "TRIAL:";
    public static final String CASE = "CASE:workId=";
    public static final String WORK = "WORK:adminId=";

    private static final Duration TRIAL_IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DRAFT_IDLE_TIMEOUT = Duration.ofHours(2);
    private static final String TOPIC_ITEM = "TOPIC";

    /* key = 묶음 · item = 합계에서 나눌 항목 (재판은 역할, null 이면 기능 이름) */
    public record Scope(String key, String item) {}

    private final ThreadLocal<Scope> current = new ThreadLocal<>();
    // 재판 시작 중 아직 trialId 가 없는 논점 생성 사용량
    private final ThreadLocal<AiUsageTotal> pendingTopic = new ThreadLocal<>();
    private final Map<String, AiUsageTotal> totals = new ConcurrentHashMap<>();

    public Scope current() {

        return current.get();
    }

    // scope 를 걸어 두고 실행 · 끝나면 이전 scope 로 되돌린다
    public Object runIn(Scope scope, ProceedingJoinPoint joinPoint) throws Throwable {

        Scope previous = current.get();
        current.set(scope);

        try {
            return joinPoint.proceed();
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }

    // 호출 한 번의 사용량을 지금 묶음에 더한다 · 묶음이 없으면 논점 생성만 따로 모아 둔다
    public void record(AiFeature feature, AiUsage usage) {

        Scope scope = current.get();

        if (scope != null) {
            totals.computeIfAbsent(scope.key(), k -> new AiUsageTotal())
                    .add(scope.item() != null ? scope.item() : feature.name(), usage);
        } else if (feature == AiFeature.TRIAL_TOPIC) {
            AiUsageTotal topic = pendingTopic.get();

            if (topic == null) {
                topic = new AiUsageTotal();
                pendingTopic.set(topic);
            }

            topic.add(TOPIC_ITEM, usage);
        }
        removeIdle(Instant.now());
    }

    public void resetPendingTopic() {
        pendingTopic.remove();
    }

    // 판이 저장되어 trialId 가 생김 → 모아 둔 논점 생성 사용량을 그 판에 붙인다
    public void attachPendingTopic(String key) {

        AiUsageTotal topic = pendingTopic.get();
        pendingTopic.remove();

        if (topic != null) {
            totals.computeIfAbsent(key, k -> new AiUsageTotal()).addAll(topic);
        }
    }

    // 묶음이 끝남 · 합계를 꺼내고 지운다
    public Optional<AiUsageTotal> take(String key) {

        return Optional.ofNullable(totals.remove(key));
    }

    private void removeIdle(Instant now) {

        totals.entrySet().removeIf(entry -> {

            Duration timeout = entry.getKey().startsWith(TRIAL) ? TRIAL_IDLE_TIMEOUT : DRAFT_IDLE_TIMEOUT;
            boolean idle = entry.getValue().lastRecordedAt().plus(timeout).isBefore(now);

            if (idle) {
                log.info("[AiUsage] 합계 (끝나지 않고 버려짐) : {}, {}", entry.getKey(), entry.getValue());
            }

            return idle;
        });
    }
}
