package com.verdict.castcourt.domain.account.service;

import com.verdict.castcourt.domain.account.dto.LoginRequestDTO;
import com.verdict.castcourt.domain.account.dto.LoginResponseDTO;
import com.verdict.castcourt.domain.account.entity.AccountEntity;
import com.verdict.castcourt.domain.account.repository.AccountRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import com.verdict.castcourt.global.security.CustomUserDetails;
import com.verdict.castcourt.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
