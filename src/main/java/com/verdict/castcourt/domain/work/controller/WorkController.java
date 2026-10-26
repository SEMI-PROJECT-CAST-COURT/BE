package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.WorkService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/works")
public class WorkController {
    private final WorkService service;
    public WorkController(WorkService service) { this.service = service; }

    public record WorkPageResponse(List<WorkListItem> content, long totalElements,
                                   int totalPages, int page, int size) {}

    @GetMapping
    public WorkPageResponse getWorks(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "1") int page) {
        var result = service.getWorks(categoryId, keyword, sort, page);
        return new WorkPageResponse(result.getContent(), result.getTotalElements(),
                result.getTotalPages(), page, 8);
    }

    @GetMapping("/{workId}")
    public WorkDetailResponse getWork(@PathVariable Integer workId) {
        return service.getWork(workId);
    }
}