package com.verdict.castcourt.global.infra.log.ai.trial;

import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.infra.log.ai.total.AiUsageScopes;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/* 재판 한 판의 AI 사용량 합계를 역할(검사 · 변호인 · 판사 · 논점)별로 남긴다
 *  - speaking.ai.model 의 AI 메서드(첫 인자 TrialSession)가 도는 동안 trialId · 역할을 걸어 둔다
 *      OpponentAi = 사용자 반대편(검사 또는 변호인) · JudgeAi(판정) · VerdictAi(총평) = 판사
 *  - 논점 생성은 trialId 가 생기기 전이라 스레드에 모아 두었다가 TrialSessionStore.save 때 그 판에 붙인다
 *  - TrialSessionStore.remove(판결 저장 · 나가기)에서 합계를 찍는다
 * */
@Slf4j
@Aspect
@Component
public class TrialAiUsageAspect {

    private final AiUsageScopes scopes;

    public TrialAiUsageAspect(AiUsageScopes scopes) {
        this.scopes = scopes;
    }

    @Around("execution(com.verdict.castcourt.global.ai.AiResult com.verdict.castcourt.domain.speaking.ai.model.*.*(..))"
            + " && args(session, ..)")
    public Object bindTrial(ProceedingJoinPoint joinPoint, TrialSession session) throws Throwable {
        return scopes.runIn(new AiUsageScopes.Scope(AiUsageScopes.TRIAL + session.getTrialId(),
                role(joinPoint, session)), joinPoint);
    }

    // 논점 생성을 새로 시작 · 이전 재판 시작이 실패해 남은 사용량이 섞이지 않게 비운다
    @Before("execution(* com.verdict.castcourt.domain.speaking.ai.model.OpenAiTopicAi.generate(..))")
    public void resetTopic() {
        scopes.resetPendingTopic();
    }

    @After("execution(* com.verdict.castcourt.domain.trial.session.TrialSessionStore.save(..)) && args(session)")
    public void attachTopic(TrialSession session) {

        scopes.attachPendingTopic(AiUsageScopes.TRIAL + session.getTrialId());
    }

    @After("execution(* com.verdict.castcourt.domain.trial.session.TrialSessionStore.remove(..)) && args(session)")
    public void flush(TrialSession session) {

        scopes.take(AiUsageScopes.TRIAL + session.getTrialId()).ifPresent(total ->
                log.info("[AiUsage] 한 판 합계 : trialId={}, mode={}, 사용자 역할={}, {}",
                        session.getTrialId(), session.getMode(), session.getRole(), total));
    }

    // 상대 AI 는 사용자 반대편, 나머지(판정 · 총평)는 판사
    private String role(ProceedingJoinPoint joinPoint, TrialSession session) {

        if (joinPoint.getTarget() instanceof OpponentAi) {
            return (session.getRole() == Side.PROSECUTION ? Side.DEFENSE : Side.PROSECUTION).name();
        }

        return "JUDGE";
    }
}
