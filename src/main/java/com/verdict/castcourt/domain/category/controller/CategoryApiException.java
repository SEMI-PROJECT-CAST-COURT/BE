package com.verdict.castcourt.domain.category.controller;

public class CategoryApiException extends RuntimeException {
    private final int status;
    private final String code;

    public CategoryApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}