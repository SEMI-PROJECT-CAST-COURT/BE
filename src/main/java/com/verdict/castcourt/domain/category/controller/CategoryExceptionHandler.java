package com.verdict.castcourt.domain.category.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AdminCategoryController.class, AdminCategoryDeleteController.class})
public class CategoryExceptionHandler {
    public record ErrorBody(int statusCode, String businessCode, String message) {}

    @ExceptionHandler(CategoryApiException.class)
    public ResponseEntity<ErrorBody> handle(CategoryApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorBody(
                exception.getStatus(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> invalidBody(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getRequestURI().endsWith("/categories/order")) {
            return ResponseEntity.badRequest().body(new ErrorBody(
                    400, "INVALID_CATEGORY_ORDER", "카테고리 순서 목록을 확인해 주세요."));
        }
        return ResponseEntity.badRequest().body(new ErrorBody(
                400, "CATEGORY_NAME_INVALID", "카테고리 이름을 입력해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(CategoryExceptionHandler.class)
                .error("카테고리 API 처리 실패", exception);
        return ResponseEntity.internalServerError().body(new ErrorBody(
                500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }}
