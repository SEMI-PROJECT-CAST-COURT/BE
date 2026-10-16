package com.verdict.castcourt.global.exception.handler;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스 계층에서 던지는 모든 비즈니스 예외 -> ErrorCode에 정의된 상태/코드/메시지 그대로 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {

        ErrorCode errorCode = e.getErrorCode();

        log.warn("[BusinessException] {} - {}", errorCode.getCode(), errorCode.getMessage());

        return toResponse(errorCode);
    }

    // @Valid @RequestBody 검증 실패 -> 첫 번째 필드 에러 메시지로 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("요청값을 확인해 주세요.");

        log.warn("[InvalidInput] {}", message);

        return toResponse(HttpStatus.BAD_REQUEST, message);
    }

    // JSON 파싱 실패, 타입 불일치, 필수 파라미터 누락, @RequestParam/@PathVariable 검증 실패
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {

        log.warn("[BadRequest] {}", e.getMessage());

        return toResponse(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {

        log.warn("[MethodNotAllowed] {}", e.getMessage());

        return toResponse(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {

        log.warn("[NotFound] {}", e.getMessage());

        return toResponse(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
    }

    // @PreAuthorize 등 컨트롤러/서비스 계층의 인가 실패 -> 없으면 아래 Exception 핸들러가 잡아 500이 됨
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {

        log.warn("[AccessDenied] {}", e.getMessage());

        return toResponse(ErrorCode.ACCESS_DENIED);
    }

    // 처리되지 않은 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {

        log.error("[UnhandledException]", e);

        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    // ErrorCode에 없는 프레임워크 레벨 에러(400/404/405) -> HTTP 상태명을 businessCode로 사용
    private ResponseEntity<ErrorResponse> toResponse(HttpStatus status, String message) {

        return ResponseEntity
                .status(status)
                .body(
                        new ErrorResponse(
                        status.value(),
                        status.name(),
                        message)
                );
    }
}
