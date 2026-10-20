package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.dto.WorkListItem;
import com.verdict.castcourt.domain.work.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/works")
public class WorkController {
    private final WorkListService service;
    private final WorkDetailService detailService;
    public WorkController(WorkListService service, WorkDetailService detailService) {
        this.service = service;
        this.detailService = detailService;
    }

    public record WorkPageResponse(List<WorkListItem> content, long totalElements,
                                   int totalPages, int page, int size) {}

    @GetMapping
    public WorkPageResponse getWorks(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new WorkListException(401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        var result = service.getWorks(categoryId, keyword, sort, page);
        return new WorkPageResponse(result.getContent(), result.getTotalElements(),
                result.getTotalPages(), page, 8);
    }

    @GetMapping("/{workId}")
    public com.verdict.castcourt.domain.work.dto.WorkDetailResponse getWork(
            @PathVariable Integer workId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new WorkListException(401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return detailService.getWork(workId);
    }}