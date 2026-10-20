package com.verdict.castcourt.domain.category.dto;
import com.verdict.castcourt.domain.category.entity.Category;

public record CategoryResponse(Integer categoryId, String name, int sortOrder, boolean fixed) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(),
                category.getSortOrder(), category.isFixed());
    }
}