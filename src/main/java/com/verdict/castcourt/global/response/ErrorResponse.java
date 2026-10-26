package com.verdict.castcourt.global.response;

import com.verdict.castcourt.global.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse {
    private int statusCode;
    private String businessCode;
    private String message;

    public ErrorResponse(int statusCode, String businessCode, String message) {
        this.statusCode = statusCode;
        this.businessCode = businessCode;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), errorCode.getMessage());
    }
}
