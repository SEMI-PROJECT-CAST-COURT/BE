package com.verdict.castcourt.domain.category.controller;
import com.verdict.castcourt.domain.category.dto.CategoryResponse;
import com.verdict.castcourt.domain.category.service.CategoryService;
import com.verdict.castcourt.global.response.ListResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
    @GetMapping
    public ListResponse<CategoryResponse> getCategories() {
        return new ListResponse<>(200, categoryService.getCategories());
    }
}