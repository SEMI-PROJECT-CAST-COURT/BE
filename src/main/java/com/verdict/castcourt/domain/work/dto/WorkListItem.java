package com.verdict.castcourt.domain.work.dto;

public record WorkListItem(Integer workId, String category, String title,
                           String posterUrl, long caseCount) {}