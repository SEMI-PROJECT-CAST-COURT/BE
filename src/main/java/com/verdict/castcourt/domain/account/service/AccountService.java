package com.verdict.castcourt.domain.account.service;

import com.verdict.castcourt.domain.account.dto.AccountDTO;
import com.verdict.castcourt.domain.account.dto.SignupDTO;
import com.verdict.castcourt.domain.account.dto.LoginRequestDTO;
import com.verdict.castcourt.domain.account.dto.LoginResponseDTO;
import com.verdict.castcourt.domain.account.entity.AccountEntity;
import com.verdict.castcourt.domain.account.repository.AccountRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.security.CustomUserDetails;
import com.verdict.castcourt.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 로그인
    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO loginRequestDto) {
        if (isBlank(loginRequestDto.getUsername()) || isBlank(loginRequestDto.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_REQUEST_INVALID);
        }

        AccountEntity account = accountRepository.findByUsername(loginRequestDto.getUsername())
                .filter(AccountEntity::isUsage)   // 삭제된 계정 제외
                .filter(a -> passwordEncoder.matches(loginRequestDto.getPassword(), a.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        CustomUserDetails user = new CustomUserDetails(
                account.getId(), account.getUsername(), null, account.getAuthority());

        String token = jwtTokenProvider.createToken(user);

        return new LoginResponseDTO(token, account.getNickname(), account.getAuthority());
    }

    // 유저 계정 생성
    @Transactional
    public void userSignup(SignupDTO dto)  { createAccount(dto, "USER"); }

    // 어드민 계정 생성
    @Transactional
    public void adminSignup(SignupDTO dto) { createAccount(dto, "ADMIN"); }

    // 계정 생성 메서드
    private void createAccount(SignupDTO dto, String authority) {
        if (isBlank(dto.getUsername()) || isBlank(dto.getPassword()) || isBlank(dto.getNickname())) {
            throw new BusinessException(ErrorCode.SIGNUP_INPUT_INVALID);
        }

        String username = dto.getUsername().trim();
        if (accountRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        AccountEntity account = new AccountEntity();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(dto.getPassword()));   // trim 안 함
        account.setNickname(dto.getNickname().trim());
        account.setAuthority(authority);

        accountRepository.save(account);
    }

    // 관리자 계정 조회
    public List<AccountDTO> findAllAdminAccounts() {
         List<AccountEntity> accounts = accountRepository.findAll(Sort.by("id"));

         return accounts
                    .stream()
                    .filter(a -> !a.getAuthority().equals("USER"))
                    .map(a -> new AccountDTO(a.getUsername(), a.getNickname(), a.isUsage()))
                    .toList();
    }

    // 관리자 계정 삭제
    @Transactional
    public void deleteAccount(int id) {
        AccountEntity account = accountRepository
                                    .findById(id)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));

        account.setUsage(false);
    }
}
