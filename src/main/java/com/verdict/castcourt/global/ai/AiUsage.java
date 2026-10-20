package com.verdict.castcourt.global.ai;

import java.time.LocalDateTime;

/* AI 호출 한 번의 사용량 기록
 *  토큰 수가 곧 비용이라 호출마다 콘솔에 남긴다.
 * */
public record AiUsage(
        String model,           // 호출한 모델
        int inputTokens,        // 보낸 토큰
        int outputTokens,       // 받은 토큰 (추론 토큰 포함)
        LocalDateTime calledAt, // 호출 시작 시각
        long elapsedMs          // 걸린 시간
) {
}