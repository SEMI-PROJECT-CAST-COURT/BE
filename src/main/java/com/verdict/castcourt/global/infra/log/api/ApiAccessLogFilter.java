package com.verdict.castcourt.global.infra.log.api;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/* 모든 API 요청을 한 줄씩 로그로 남긴다 (logs/api-access.log · logback-spring.xml)
 *  호출량 지표(http_server_requests)는 스프링 부트가 이미 만든다. 이 로그는 "누가(ip) 어떤 주소(path)를 불렀는지"를 확인하는 용도다.
 *  uri 는 지표의 uri 태그와 같은 주소 패턴(/api/works/{workId})이라 대시보드의 주소로 바로 찾을 수 있다.
 *  필터 순서
 *  - 스프링 시큐리티보다 먼저 돌아야 인증 실패(401) · 권한 없음(403)으로 막힌 요청까지 남는다
 *  - 그래서 로그인 사용자는 알 수 없다 (시큐리티가 끝나면 SecurityContext 를 비운다). 대신 ip 를 남긴다
 *  SSE(발언 제출)는 컨트롤러가 먼저 끝나므로 스트림이 닫힐 때(AsyncListener.onComplete) 찍는다
 *  로그 레벨
 *  - INFO : 요청 한 건 (4xx 포함 · 클라이언트 잘못은 서버가 대응할 일이 아니다)
 *  - WARN : 5xx · 처리 도중 예외 (원인은 GlobalExceptionHandler 의 ERROR 로그에 있다)
 * */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    // 프로메테우스 수집(5초마다) · 문서 · 정적 파일은 API 호출이 아니라 뺀다
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/actuator", "/swagger-ui", "/v3/api-docs", "/uploads", "/images", "/favicon.ico"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        // 브라우저가 CORS 확인용으로 먼저 보내는 요청 (실제 호출이 아니다)
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            if (!failed && request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new CompleteListener(request, response, start));
            } else {
                // 예외가 필터 밖으로 나가면 상태 코드가 아직 정해지지 않았다. 톰캣이 500 으로 응답한다
                write(request, failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus(), start, failed);
            }
        }
    }

    // uri=주소 패턴(컨트롤러를 찾지 못했으면 -), path=실제 주소 · 쿼리 문자열은 민감한 값이 섞일 수 있어 남기지 않는다
    private static void write(HttpServletRequest request, int status, long start, boolean failed) {

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String uri = pattern == null ? "-" : pattern.toString();

        if (failed || status >= 500) {
            log.warn("[ApiAccess] method={}, uri={}, path={}, status={}, {}ms, ip={}{}",
                    request.getMethod(), uri, request.getRequestURI(), status, elapsedMs, request.getRemoteAddr(),
                    failed ? " (처리 도중 예외)" : "");
        } else {
            log.info("[ApiAccess] method={}, uri={}, path={}, status={}, {}ms, ip={}",
                    request.getMethod(), uri, request.getRequestURI(), status, elapsedMs, request.getRemoteAddr());
        }
    }

    // SSE 스트림이 끝날 때 한 번 찍는다 · 타임아웃 · 오류로 끝나도 onComplete 는 마지막에 불린다
    private record CompleteListener(HttpServletRequest request, HttpServletResponse response, long start)
            implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            write(request, response.getStatus(), start, false);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
        }

        @Override
        public void onError(AsyncEvent event) {
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
        }
    }
}
