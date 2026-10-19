package com.verdict.castcourt.domain.evidencecard.controller;

import com.verdict.castcourt.domain.evidencecard.service.EvidenceCardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceCardController {
    private final EvidenceCardService evidenceCardService;

    @Autowired
    public EvidenceCardController(EvidenceCardService evidenceCardService) {
        this.evidenceCardService = evidenceCardService;
    }
}
