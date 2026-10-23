package com.verdict.castcourt.global.infra.log.ai;

import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.infra.log.ai.total.AiUsageScopes;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/* 모든 AI 호출의 사용량을 한 줄씩 로그로 남긴다
 *  OpenAiClient(createJson · streamText)를 가로채 기능(AiFeature) · 모델 · 토큰 · 걸린 시간을 찍고,
 *  지금 묶음(재판 · 사건 · 작품)이 있으면 AiUsageScopes 에 더한다. 묶음별 합계는 trial / draft 패키지의 aspect 가 찍는다.
 *  기능은 OpenAiClient 를 부른 클래스로 정한다 (호출 스택에서 AiFeature 에 등록된 첫 클래스)
 *  로그 레벨
 *  - TRACE : AI 호출 시작 (평소에는 꺼둔다)
 *  - INFO  : 호출 한 번의 사용량 · 묶음 합계 (비용 집계 · 모니터링의 기준이라 운영에서도 남긴다)
 *  - WARN  : 느린 호출 · 일시적인 AI 실패 (타임아웃 · OpenAI 오류)
 *  - ERROR : 즉시 대응이 필요한 실패 (API 키 설정 문제 · 예상하지 못한 예외)
 *  AI 코드는 고치지 않는다. 실패한 호출은 사용량이 없으므로 합계에 넣지 않는다.
 * */
@Slf4j
@Aspect
@Component
public class AiUsageAspect {

    private static final Duration SLOW_CALL = Duration.ofSeconds(20);   // OpenAiClient 응답 대기 상한(60초)의 1/3

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final AiUsageScopes scopes;

    public AiUsageAspect(AiUsageScopes scopes) {
        this.scopes = scopes;
    }

    private record Caller(AiFeature feature, String method) {

        String featureName() {
            return feature == null ? "UNKNOWN" : feature.name();
        }
    }

    @Around("execution(com.verdict.castcourt.global.ai.AiResult com.verdict.castcourt.global.ai.OpenAiClient.*(..))")
    public Object record(ProceedingJoinPoint joinPoint) throws Throwable {

        Caller caller = caller();
        String context = context(scopes.current());
        log.trace("[AiUsage] 호출 시작 : feature={}, caller={}{}", caller.featureName(), caller.method(), context);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (BusinessException e) {
            failed(caller, context, e.getErrorCode());
            throw e;
        } catch (Throwable e) {
            log.error("[AiUsage] 호출 실패 (예상하지 못한 예외) : feature={}, caller={}{}",
                    caller.featureName(), caller.method(), context, e);
            throw e;
        }

        if (result instanceof AiResult<?> aiResult && aiResult.usage() != null) {
            AiUsage usage = aiResult.usage();
            log.info("[AiUsage] 호출 완료 : feature={}, caller={}{}, model={}, 입력 토큰={}, 출력 토큰={}, {}ms",
                    caller.featureName(), caller.method(), context,
                    usage.model(), usage.inputTokens(), usage.outputTokens(), usage.elapsedMs());

            if (usage.elapsedMs() > SLOW_CALL.toMillis()) {
                log.warn("[AiUsage] 느린 호출 : feature={}, caller={}{}, {}ms (기준 {}ms)",
                        caller.featureName(), caller.method(), context, usage.elapsedMs(), SLOW_CALL.toMillis());
            }
            if (caller.feature() != null) {
                scopes.record(caller.feature(), usage);
            }
        }
        return result;
    }

    // ", scope=TRIAL:abc, role=JUDGE" · 묶음이 없으면 빈 문자열
    private String context(AiUsageScopes.Scope scope) {

        if (scope == null) {
            return "";
        }
        return ", scope=" + scope.key() + (scope.item() == null ? "" : ", role=" + scope.item());
    }

    // 호출 스택에서 AiFeature 에 등록된 첫 클래스 · 없으면 domain 패키지의 첫 클래스 이름 (새 기능 추가를 놓친 경우)
    private Caller caller() {

        return STACK_WALKER.walk(frames -> frames
                .map(frame -> AiFeature.of(frame.getDeclaringClass())
                        .map(feature -> new Caller(feature,
                                frame.getDeclaringClass().getSimpleName() + "." + frame.getMethodName())))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(this::unknownCaller));
    }

    private Caller unknownCaller() {

        return STACK_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type.getName().startsWith("com.verdict.castcourt.domain."))
                .filter(type -> !type.getName().contains("$$"))
                .findFirst()
                .map(type -> new Caller(null, type.getSimpleName()))
                .orElse(new Caller(null, "-")));
    }

    // API 키 문제는 고칠 때까지 모든 호출이 실패하므로 ERROR, 타임아웃 · OpenAI 오류는 일시적일 수 있어 WARN
    private void failed(Caller caller, String context, ErrorCode errorCode) {

        if (errorCode == ErrorCode.AI_CONFIGURATION_MISSING) {
            log.error("[AiUsage] 호출 실패 (AI 설정 확인 필요) : feature={}, caller={}{}, code={}",
                    caller.featureName(), caller.method(), context, errorCode.getCode());
        } else {
            log.warn("[AiUsage] 호출 실패 : feature={}, caller={}{}, code={}",
                    caller.featureName(), caller.method(), context, errorCode.getCode());
        }
    }
}
