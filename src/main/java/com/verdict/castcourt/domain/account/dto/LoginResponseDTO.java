package com.verdict.castcourt.domain.account.dto;

public class LoginResponseDTO {
    private String token;
    private String nickname;
    private String authority;

    public LoginResponseDTO(String token, String nickname, String authority) {
        this.token = token;
        this.nickname = nickname;
        this.authority = authority;
    }

    public String getToken() {
        return token;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAuthority() {
        return authority;
    }
}
