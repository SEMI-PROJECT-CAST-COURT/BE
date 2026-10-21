package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.WorkSummaryDraftService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/works")
public class WorkSummaryDraftController {
    private final WorkSummaryDraftService service;
    public WorkSummaryDraftController(WorkSummaryDraftService service) { this.service = service; }

    @PostMapping(value = "/summary-draft", consumes = "application/json")
    public WorkSummaryDraftResponse generate(@RequestBody WorkSummaryDraftRequest request) {
        return service.generate(request);
    }
}