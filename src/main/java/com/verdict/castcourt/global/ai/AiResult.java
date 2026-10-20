package com.verdict.castcourt.global.ai;

/* AI 호출 결과 = 결과물 + 사용량
 *  결과물 모양은 호출마다 달라서(판정 · 대사 · 총평) 제네릭 T 로 받는다.
 * */
public record AiResult<T>(
        T output,
        AiUsage usage
) {
}