package com.verdict.castcourt.global.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ErrorCode {

    // ==================== Common ====================
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다."),

    // ==================== Auth ====================
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "유효하지 않은 인증 정보입니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "로그인 정보가 만료되었습니다."),
    LOGIN_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "LOGIN_REQUEST_INVALID", "아이디와 비밀번호를 입력해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 일치하지 않습니다."),
    SIGNUP_INPUT_INVALID(HttpStatus.BAD_REQUEST, "SIGNUP_INPUT_INVALID", "회원가입 입력값을 확인해 주세요."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "이미 사용 중인 아이디입니다."),

    // ==================== Admin ====================
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "관리자 계정을 찾을 수 없습니다."),
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "관리자 계정 입력값을 확인해 주세요."),
    SUPER_ADMIN_DELETE_FORBIDDEN(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_DELETE_FORBIDDEN", "슈퍼 관리자는 삭제할 수 없습니다."),

    // ==================== Category ====================
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATE(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_DUPLICATE", "이미 존재하는 카테고리 이름입니다."),
    CATEGORY_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_TOO_LONG", "카테고리 이름은 10자 이하여야 합니다."),
    FIXED_CATEGORY_IMMUTABLE(HttpStatus.BAD_REQUEST, "FIXED_CATEGORY_IMMUTABLE", "미분류 카테고리는 수정할 수 없습니다."),
    FIXED_CATEGORY_DELETE_FORBIDDEN(HttpStatus.BAD_REQUEST, "FIXED_CATEGORY_DELETE_FORBIDDEN", "미분류 카테고리는 삭제할 수 없습니다."),
    INVALID_CATEGORY_ORDER(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_ORDER", "카테고리 순서 목록을 확인해 주세요."),

    // ==================== Work ====================
    WORK_NOT_FOUND(HttpStatus.NOT_FOUND, "WORK_NOT_FOUND", "작품을 찾을 수 없습니다."),
    WORK_INPUT_INVALID(HttpStatus.BAD_REQUEST, "WORK_INPUT_INVALID", "작품 정보를 확인해주세요"),
    WORK_TITLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "WORK_TITLE_ALREADY_EXISTS", "이미 등록된 작품명입니다."),
    INVALID_WORK_LIST_QUERY(HttpStatus.BAD_REQUEST, "INVALID_WORK_LIST_QUERY", "작품 목록 검색 조건을 확인해 주세요."),
    WORK_DRAFT_INPUT_INVALID(HttpStatus.BAD_REQUEST, "WORK_DRAFT_INPUT_INVALID", "작품 제목과 원작자를 입력해 주세요."),
    POSTER_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "POSTER_INVALID_FORMAT", "포스터는 JPG 또는 PNG만 등록할 수 있습니다."),
    POSTER_TOO_LARGE(HttpStatus.BAD_REQUEST, "POSTER_TOO_LARGE", "포스터는 5MB 이하여야 합니다."),

    // ==================== Case ====================
    CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다."),
    CASE_INPUT_INVALID(HttpStatus.BAD_REQUEST, "CASE_INPUT_INVALID", "사건 정보를 확인해주세요"),
    CASE_CARD_COUNT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "CASE_CARD_COUNT_INSUFFICIENT", "검사와 변호인 측 근거 카드를 각각 3장 이상 등록해 주세요."),
    CASE_PROGRESS_TOO_LONG(HttpStatus.BAD_REQUEST, "CASE_PROGRESS_TOO_LONG", "사건 경과는 1000자 이하여야 합니다."),
    CARD_WEIGHT_INVALID(HttpStatus.BAD_REQUEST, "CARD_WEIGHT_INVALID", "근거 카드 가중치는 1~20이어야 합니다."),
    PENALTY_TIER_COUNT_INVALID(HttpStatus.BAD_REQUEST, "PENALTY_TIER_COUNT_INVALID", "형벌표는 6단계를 모두 등록해야 합니다."),

    // ==================== Trial ====================
    TRIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIAL_NOT_FOUND", "진행 중인 재판을 찾을 수 없습니다."),
    TRIAL_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIAL_RECORD_NOT_FOUND", "재판 기록을 찾을 수 없습니다."),
    TRIAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIAL_ACCESS_DENIED", "다른 사용자의 재판에 접근할 수 없습니다."),
    TRIAL_ROLE_INVALID(HttpStatus.BAD_REQUEST, "TRIAL_ROLE_INVALID", "재판 역할을 확인해 주세요."),
    TRIAL_MODE_INVALID(HttpStatus.BAD_REQUEST, "TRIAL_MODE_INVALID", "재판 모드를 확인해 주세요."),
    VERDICT_STAGE_INVALID(HttpStatus.BAD_REQUEST, "VERDICT_STAGE_INVALID", "최후 변론 후에 판결을 요청할 수 있습니다."),
    ROLE_ALREADY_RECORDED(HttpStatus.CONFLICT, "ROLE_ALREADY_RECORDED", "이미 기록이 있는 역할"),
    INVALID_TRIAL_LIST_FILTER(HttpStatus.BAD_REQUEST, "INVALID_TRIAL_LIST_FILTER", "재판 목록 필터를 확인해 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "다음 페이지 조회 위치가 잘못되었습니다."),

    // ==================== Speaking ====================
    SPEAKING_NOT_YOUR_TURN(HttpStatus.BAD_REQUEST, "SPEAKING_NOT_YOUR_TURN", "지금은 발언할 차례가 아닙니다."),
    SPEAKING_CONTENT_INVALID(HttpStatus.BAD_REQUEST, "SPEAKING_CONTENT_INVALID", "발언은 1자 이상 300자 이하로 입력해 주세요."),

    // ==================== AI ====================
    AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR", "AI 응답 처리에 실패했습니다."),
    AI_JUDGE_FAILED(HttpStatus.BAD_GATEWAY, "AI_JUDGE_FAILED", "제대로 답변을 생성하지 못했습니다."),
    AI_OPPONENT_FAILED(HttpStatus.BAD_GATEWAY, "AI_OPPONENT_FAILED", "제대로 답변을 생성하지 못했습니다."),
    AI_CONFIGURATION_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "AI_CONFIGURATION_MISSING", "AI 서비스 설정을 확인해 주세요."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 응답 시간이 초과되었습니다."),

    // ==================== WebSocket ====================
    WS_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "WS_AUTHENTICATION_FAILED", "관리자 인증에 실패했습니다."),
    WS_DRAFT_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "WS_DRAFT_REQUEST_INVALID", "사건 초안 요청값을 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
