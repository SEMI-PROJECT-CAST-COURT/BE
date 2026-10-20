package com.verdict.castcourt.domain.work.controller;

import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/works")
public class AdminWorkController {
    private final WorkCreateService service;
    private final WorkUpdateService updateService;

    public AdminWorkController(WorkCreateService service, WorkUpdateService updateService) {
        this.service = service;
        this.updateService = updateService;

    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkCreateResponse> create(
            @RequestPart("data") WorkCreateRequest data,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.status(201).body(new WorkCreateResponse(service.create(data, poster)));
    }

    @PutMapping(value = "/{workId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable Integer workId,
            @RequestPart("data") WorkUpdateRequest data,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            Authentication authentication) {
        requireAdmin(authentication);
        updateService.update(workId, data, poster);
        return ResponseEntity.ok().build();
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken)
            throw new WorkListException(401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        if (authentication.getAuthorities().stream().noneMatch(authority ->
                Set.of("ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN").contains(authority.getAuthority())))
            throw new WorkListException(403, "ACCESS_DENIED", "접근 권한이 없습니다.");
    }
}
