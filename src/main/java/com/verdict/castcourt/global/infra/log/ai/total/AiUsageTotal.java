package com.verdict.castcourt.global.infra.log.ai.total;

import com.verdict.castcourt.global.ai.AiUsage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/* 한 묶음(재판 한 판 · 사건 하나 · 작품 하나)의 AI 사용량 합계 (항목별)
 *  항목 = 재판은 역할(PROSECUTION · DEFENSE · JUDGE · TOPIC), 작품 · 사건은 기능(AiFeature 이름)
 *  같은 묶음에 요청이 겹칠 수 있어서 메서드를 synchronized 로 둔다.
 * */
public class AiUsageTotal {

    private final Map<String, ItemUsage> items = new LinkedHashMap<>();
    private volatile Instant lastRecordedAt = Instant.now();   // 버려진 묶음 정리 기준

    synchronized void add(String item, AiUsage usage) {
        items.computeIfAbsent(item, i -> new ItemUsage()).add(usage);
        lastRecordedAt = Instant.now();
    }

    // 다른 합계를 통째로 더한다 (재판 시작 때 모아 둔 논점 생성 사용량)
    synchronized void addAll(AiUsageTotal other) {
        other.items.forEach((item, usage) -> items.computeIfAbsent(item, i -> new ItemUsage()).addAll(usage));
        lastRecordedAt = Instant.now();
    }

    Instant lastRecordedAt() {
        return lastRecordedAt;
    }

    // 예) JUDGE(호출 11, 입력 15300, 출력 2400, 30211ms) DEFENSE(...) 합계(호출 17, 입력 23500, 출력 4300)
    @Override
    public synchronized String toString() {

        int calls = items.values().stream().mapToInt(i -> i.calls).sum();
        int input = items.values().stream().mapToInt(i -> i.inputTokens).sum();
        int output = items.values().stream().mapToInt(i -> i.outputTokens).sum();

        String detail = items.entrySet().stream()
                .map(e -> e.getKey() + e.getValue())
                .collect(Collectors.joining(" "));

        return detail + " 합계(호출 " + calls + ", 입력 " + input + ", 출력 " + output + ")";
    }

    private static class ItemUsage {

        private int calls;
        private int inputTokens;
        private int outputTokens;
        private long elapsedMs;

        void add(AiUsage usage) {
            calls++;
            inputTokens += usage.inputTokens();
            outputTokens += usage.outputTokens();
            elapsedMs += usage.elapsedMs();
        }

        void addAll(ItemUsage other) {
            calls += other.calls;
            inputTokens += other.inputTokens;
            outputTokens += other.outputTokens;
            elapsedMs += other.elapsedMs;
        }

        @Override
        public String toString() {
            return "(호출 " + calls + ", 입력 " + inputTokens + ", 출력 " + outputTokens + ", " + elapsedMs + "ms)";
        }
    }
}
