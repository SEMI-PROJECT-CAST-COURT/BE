package com.verdict.castcourt.domain.category.controller;

import com.verdict.castcourt.domain.category.dto.*;
import com.verdict.castcourt.domain.category.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final CategoryService service;
    public AdminCategoryController(CategoryService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CategoryCreateResponse> create(@RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(201).body(new CategoryCreateResponse(service.createCategory(request.name())));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<Void> rename(@PathVariable Integer categoryId,
                                       @RequestBody CategoryRenameRequest request) {
        service.renameCategory(categoryId, request.name());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/order")
    public ResponseEntity<Void> reorder(@RequestBody CategoryOrderRequest request) {
        service.reorderCategories(request.categoryIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Integer categoryId) {
        service.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}