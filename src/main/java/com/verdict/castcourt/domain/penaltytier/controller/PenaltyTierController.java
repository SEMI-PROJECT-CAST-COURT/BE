package com.verdict.castcourt.domain.penaltytier.controller;

import com.verdict.castcourt.domain.penaltytier.service.PenaltyTierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PenaltyTierController {
    private final PenaltyTierService penaltyTierService;

    @Autowired
    public PenaltyTierController(PenaltyTierService penaltyTierService) {
        this.penaltyTierService = penaltyTierService;
    }
}
