package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.WorkService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/works")
public class AdminWorkController {
    private final WorkService service;
    public AdminWorkController(WorkService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkCreateResponse> create(
            @RequestPart("data") WorkCreateRequest data,
            @RequestPart(value = "poster", required = false) MultipartFile poster) {
        return ResponseEntity.status(201).body(new WorkCreateResponse(service.create(data, poster)));
    }

    @PutMapping(value = "/{workId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(@PathVariable Integer workId,
            @RequestPart("data") WorkUpdateRequest data,
            @RequestPart(value = "poster", required = false) MultipartFile poster) {
        service.update(workId, data, poster);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{workId}")
    public ResponseEntity<Void> delete(@PathVariable Integer workId) {
        service.delete(workId);
        return ResponseEntity.noContent().build();
    }
}