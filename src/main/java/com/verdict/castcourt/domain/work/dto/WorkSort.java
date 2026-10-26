package com.verdict.castcourt.domain.work.dto;

public enum WorkSort {
    LATEST("w.id DESC"),
    RELEASED("w.released_on DESC, w.id DESC"),
    NAME("w.title ASC, w.id DESC");

    private final String orderBy;
    WorkSort(String orderBy) { this.orderBy = orderBy; }
    public String orderBy() { return orderBy; }
}