package com.verdict.castcourt.global.ai;

import java.time.LocalDateTime;

/* AI 호출 한 번의 사용량 기록
 *  토큰 수가 곧 비용이라 호출마다 남긴다. 지금은 콘솔에만 찍고,
 *  ai_call_log 테이블이 생기면 이 값을 그대로 저장한다. // TODO(연결) 8-4 AI 호출 기록 (박찬웅)
 * */
public record AiUsage(
        String model,           // 호출한 모델 (가짜 AI 는 "fake")
        int inputTokens,        // 보낸 토큰
        int outputTokens,       // 받은 토큰 (추론 토큰 포함)
        LocalDateTime calledAt, // 호출 시작 시각
        long elapsedMs          // 걸린 시간
) {

    /* 가짜 AI 용 : 토큰을 쓰지 않았으니 0 으로 채운다 */
    public static AiUsage fake(LocalDateTime calledAt, long elapsedMs) {
        return new AiUsage("fake", 0, 0, calledAt, elapsedMs);
    }
}