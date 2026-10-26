package com.verdict.castcourt.global.response;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
public class PageResponse<T> {
    private int statusCode;
    private List<T> result;
    private long total;
    private int totalPage;

    public PageResponse(int statusCode, List<T> result, long total, int totalPage) {
        this.statusCode = statusCode;
        this.result = result;
        this.total = total;
        this.totalPage = totalPage;
    }
}
