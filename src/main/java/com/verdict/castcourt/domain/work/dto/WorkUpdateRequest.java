package com.verdict.castcourt.domain.work.dto;
import java.time.LocalDate;

public record WorkUpdateRequest(Integer categoryId, String title, String author,
                                LocalDate releasedOn, String summary) {}