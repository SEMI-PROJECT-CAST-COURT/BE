package com.verdict.castcourt.global.response;

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
}
