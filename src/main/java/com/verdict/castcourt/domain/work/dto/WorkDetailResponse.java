package com.verdict.castcourt.domain.work.dto;

import java.time.LocalDate;

public record WorkDetailResponse(Integer workId, String title, String category,
                                 String author, LocalDate releasedOn,
                                 String summary, String posterUrl) {}