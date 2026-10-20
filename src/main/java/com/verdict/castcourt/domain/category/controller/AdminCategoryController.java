package com.verdict.castcourt.domain.category.controller;

import com.verdict.castcourt.domain.category.dto.CategoryCreateRequest;
import com.verdict.castcourt.domain.category.dto.CategoryCreateResponse;
import com.verdict.castcourt.domain.category.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryCreateResponse> create(
            @RequestBody CategoryCreateRequest request, Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.status(201).body(
                new CategoryCreateResponse(categoryService.createCategory(request.name())));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<Void> rename(@PathVariable Integer categoryId,
            @RequestBody com.verdict.castcourt.domain.category.dto.CategoryRenameRequest request,
            Authentication authentication) {
        requireAdmin(authentication);
        categoryService.renameCategory(categoryId, request.name());
        return ResponseEntity.ok().build();
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new CategoryApiException(401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        boolean admin = authentication.getAuthorities().stream().anyMatch(authority ->
                java.util.Set.of("ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .contains(authority.getAuthority()));
        if (!admin) {
            throw new CategoryApiException(403, "ACCESS_DENIED", "접근 권한이 없습니다.");
        }

    }
}