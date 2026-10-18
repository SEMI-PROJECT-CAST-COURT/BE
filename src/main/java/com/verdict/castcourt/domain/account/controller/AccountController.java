package com.verdict.castcourt.domain.account.controller;

import com.verdict.castcourt.domain.account.dto.LoginRequestDTO;
import com.verdict.castcourt.domain.account.dto.LoginResponseDTO;
import com.verdict.castcourt.domain.account.service.AccountService;
import com.verdict.castcourt.global.response.SingleResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "로그인",
            description = "아이디와 비밀번호로 로그인하고 JWT를 발급합니다. " +
                    "authority는 일반 회원 USER(기본값), 관리자 ADMIN, 슈퍼 어드민 SUPER_ADMIN입니다.")
    @PostMapping("/login")
    public ResponseEntity<SingleResponse<LoginResponseDTO>> login(
            @RequestBody LoginRequestDTO loginRequestDTO
    ) {
        LoginResponseDTO loginResponseDto = accountService.login(loginRequestDTO);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), loginResponseDto));
    }
}
