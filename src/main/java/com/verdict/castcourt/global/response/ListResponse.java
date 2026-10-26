package com.verdict.castcourt.global.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ListResponse<T> {
    private int statusCode;
    private List<T> result;

    public ListResponse(int statusCode, List<T> result) {
        this.statusCode = statusCode;
        this.result = result;
    }
}
