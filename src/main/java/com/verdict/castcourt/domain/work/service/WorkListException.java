package com.verdict.castcourt.domain.work.service;

public class WorkListException extends RuntimeException {
    private final int status;
    private final String code;
    public WorkListException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}