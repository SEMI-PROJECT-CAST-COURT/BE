package com.verdict.castcourt.domain.category.controller;

import com.verdict.castcourt.domain.category.service.CategoryDeleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryDeleteController {
    private final CategoryDeleteService service;
    public AdminCategoryDeleteController(CategoryDeleteService service) { this.service = service; }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Integer categoryId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken)
            throw new CategoryApiException(401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        if (authentication.getAuthorities().stream().noneMatch(authority ->
                Set.of("ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN").contains(authority.getAuthority())))
            throw new CategoryApiException(403, "ACCESS_DENIED", "접근 권한이 없습니다.");
        service.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}