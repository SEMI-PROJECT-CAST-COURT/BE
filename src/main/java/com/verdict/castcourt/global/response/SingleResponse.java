package com.verdict.castcourt.global.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SingleResponse<T> {
    private int statusCode;
    private T result;

    public SingleResponse(int statusCode, T result) {
        this.statusCode = statusCode;
        this.result = result;
    }
}
