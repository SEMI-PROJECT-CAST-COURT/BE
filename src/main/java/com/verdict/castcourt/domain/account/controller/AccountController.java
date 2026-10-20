package com.verdict.castcourt.domain.account.controller;

import com.verdict.castcourt.domain.account.dto.AccountDTO;
import com.verdict.castcourt.domain.account.dto.SignupDTO;
import com.verdict.castcourt.domain.account.dto.LoginRequestDTO;
import com.verdict.castcourt.domain.account.dto.LoginResponseDTO;
import com.verdict.castcourt.domain.account.service.AccountService;
import com.verdict.castcourt.global.response.ListResponse;
import com.verdict.castcourt.global.response.SingleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "계정")
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

    @Operation(summary = "유저 회원가입",
            description = "일반 회원 계정을 생성합니다. 로그인 없이 요청할 수 있습니다.")
    @PostMapping("/signup/user")
    public ResponseEntity<SingleResponse<String>> userSignup(
            @RequestBody SignupDTO signupDTO
    ) {
        accountService.userSignup(signupDTO);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), "회원가입 성공"));
    }

    @Operation(summary = "관리자 계정 생성",
            description = "관리자 계정을 생성합니다. 슈퍼어드민 권한이 있는 사용자만 생성할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/signup/admin")
    public ResponseEntity<SingleResponse<String>> adminSignup(
            @RequestBody SignupDTO signupDTO
    ) {
        accountService.adminSignup(signupDTO);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), "회원가입 성공"));
    }

    @Operation(summary = "관리자 목록 조회",
            description = "전체 관리자 계정 목록을 가져옵니다. 슈퍼어드민 권한이 있는 사용자만 조회 가능합니다.")
    @GetMapping("/account/admin")
    public ResponseEntity<ListResponse<AccountDTO>> findAllFields() {
        List<AccountDTO> accounts = accountService.findAllAdminAccounts();

        return ResponseEntity.ok(new ListResponse<>(HttpStatus.OK.value(), accounts));
    }

    @Operation(summary = "관리자 계정 비활성화",
            description = "관리자 계정을 비활성화합니다. 슈퍼어드민 권한이 있는 사용자만 비활성화 할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<SingleResponse<String>> deleteAccount(
            @PathVariable int id
    ) {
        accountService.deleteAccount(id);

        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), "계정 비활성화 성공"));
    }
}
