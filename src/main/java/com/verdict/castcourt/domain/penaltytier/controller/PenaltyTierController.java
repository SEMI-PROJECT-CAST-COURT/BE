package com.verdict.castcourt.domain.penaltytier.controller;

import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyTierService;
import com.verdict.castcourt.global.response.ListResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PenaltyTierController {
    private final PenaltyTierService penaltyTierService;

    @Autowired
    public PenaltyTierController(PenaltyTierService penaltyTierService) {
        this.penaltyTierService = penaltyTierService;
    }
}
