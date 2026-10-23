package com.verdict.castcourt.global.infra.log.ai.draft;

import com.verdict.castcourt.domain.evidencecard.dto.CardDraftRequestDTO;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyDraftRequestDTO;
import com.verdict.castcourt.domain.work.dto.WorkCreateRequest;
import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseRequestDTO;
import com.verdict.castcourt.global.infra.log.ai.total.AiUsageScopes;
import com.verdict.castcourt.global.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/* 작품 하나 · 사건 하나를 만들 때 쓴 AI 사용량 합계를 기능별로 남긴다
 *  사건 (묶음 = workId) : 사건 초안(쟁점 후보 · 다시 쓰기) · 근거 카드 초안 · 형벌표 초안 → 사건 등록 때 합계
 *      초안 요청은 모두 workId 를 들고 오고, 사건 등록 요청도 workId 를 들고 온다.
 *  작품 (묶음 = 관리자 id) : 작품 소개 초안 → 작품 등록 · 수정 때 합계
 *      작품 소개 초안은 작품이 생기기 전이라 workId 가 없어서, 같은 관리자의 다음 작품 등록에 붙인다.
 *  등록하지 않고 떠난 묶음은 AiUsageScopes 가 2시간 뒤 "버려짐"으로 찍는다.
 * */
@Slf4j
@Aspect
@Component
public class DraftAiUsageAspect {

    private final AiUsageScopes scopes;

    public DraftAiUsageAspect(AiUsageScopes scopes) {
        this.scopes = scopes;
    }

    /* ===== 사건 ===== */

    @Around("(execution(* com.verdict.castcourt.domain.workcase.service.CaseDraftService.candidates(..))"
            + " || execution(* com.verdict.castcourt.domain.workcase.service.CaseDraftService.rewrite(..)))"
            + " && args(workId, ..)")
    public Object bindCaseDraft(ProceedingJoinPoint joinPoint, int workId) throws Throwable {
        return scopes.runIn(caseScope(workId), joinPoint);
    }

    @Around("(execution(* com.verdict.castcourt.domain.evidencecard.service.CardDraftService.generate(..))"
            + " || execution(* com.verdict.castcourt.domain.evidencecard.service.CardDraftService.suggest(..)))"
            + " && args(request)")
    public Object bindCardDraft(ProceedingJoinPoint joinPoint, CardDraftRequestDTO request) throws Throwable {
        return scopes.runIn(caseScope(request.workId()), joinPoint);
    }

    @Around("execution(* com.verdict.castcourt.domain.penaltytier.service.PenaltyDraftService.generate(..))"
            + " && args(request)")
    public Object bindPenaltyDraft(ProceedingJoinPoint joinPoint, PenaltyDraftRequestDTO request) throws Throwable {
        return scopes.runIn(caseScope(request.workId()), joinPoint);
    }

    @AfterReturning(pointcut = "execution(* com.verdict.castcourt.domain.workcase.service.WorkCaseService.saveWorkCase(..))"
            + " && args(request)", returning = "caseId")
    public void flushCase(WorkCaseRequestDTO request, Integer caseId) {
        scopes.take(AiUsageScopes.CASE + request.workId()).ifPresent(total ->
                log.info("[AiUsage] 사건 하나 합계 : workId={}, caseId={}, {}", request.workId(), caseId, total));
    }

    /* ===== 작품 ===== */

    @Around("execution(* com.verdict.castcourt.domain.work.service.WorkSummaryDraftService.generate(..))")
    public Object bindWorkDraft(ProceedingJoinPoint joinPoint) throws Throwable {
        return scopes.runIn(workScope(), joinPoint);
    }

    @AfterReturning(pointcut = "execution(* com.verdict.castcourt.domain.work.service.WorkService.create(..))"
            + " && args(request, ..)", returning = "workId")
    public void flushWorkCreate(WorkCreateRequest request, Integer workId) {
        scopes.take(workScope().key()).ifPresent(total ->
                log.info("[AiUsage] 작품 하나 합계 (등록) : workId={}, title={}, {}", workId, request.title(), total));
    }

    @AfterReturning("execution(* com.verdict.castcourt.domain.work.service.WorkService.update(..)) && args(workId, ..)")
    public void flushWorkUpdate(Integer workId) {
        scopes.take(workScope().key()).ifPresent(total ->
                log.info("[AiUsage] 작품 하나 합계 (수정) : workId={}, {}", workId, total));
    }

    private AiUsageScopes.Scope caseScope(int workId) {
        return new AiUsageScopes.Scope(AiUsageScopes.CASE + workId, null);
    }

    // 작품 소개 초안 · 작품 등록은 모두 관리자 HTTP 요청이라 로그인한 관리자 id 를 쓴다
    private AiUsageScopes.Scope workScope() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String adminId = authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user
                ? String.valueOf(user.getId())
                : "unknown";
        return new AiUsageScopes.Scope(AiUsageScopes.WORK + adminId, null);
    }
}
