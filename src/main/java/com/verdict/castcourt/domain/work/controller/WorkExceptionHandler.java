package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.service.WorkListException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {WorkController.class, AdminWorkController.class})
public class WorkExceptionHandler {
    public record ErrorBody(int status, String code, String message) {}

    @ExceptionHandler(WorkListException.class)
    public ResponseEntity<ErrorBody> handle(WorkListException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorBody(
                exception.getStatus(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> invalidQuery() {
        return ResponseEntity.badRequest().body(new ErrorBody(
                400, "INVALID_WORK_LIST_QUERY", "작품 목록 검색 조건을 확인해 주세요."));
    }

    @ExceptionHandler({
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorBody> invalidInput() {
        return ResponseEntity.badRequest().body(new ErrorBody(
                400, "WORK_INPUT_INVALID", "작품 정보를 확인해주세요"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> oversizedPoster() {
        return ResponseEntity.badRequest().body(new ErrorBody(
                400, "POSTER_TOO_LARGE", "포스터는 100KB 이하여야 합니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(WorkExceptionHandler.class)
                .error("작품 API 처리 실패", exception);
        return ResponseEntity.internalServerError().body(new ErrorBody(
                500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }}