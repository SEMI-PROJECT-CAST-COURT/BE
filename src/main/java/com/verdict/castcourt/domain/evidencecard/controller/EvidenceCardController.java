package com.verdict.castcourt.domain.evidencecard.controller;

import com.verdict.castcourt.domain.account.dto.LoginResponseDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.service.EvidenceCardService;
import com.verdict.castcourt.global.response.SingleResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceCardController {
    private final EvidenceCardService evidenceCardService;

    @Autowired
    public EvidenceCardController(EvidenceCardService evidenceCardService) {
        this.evidenceCardService = evidenceCardService;
    }
}
