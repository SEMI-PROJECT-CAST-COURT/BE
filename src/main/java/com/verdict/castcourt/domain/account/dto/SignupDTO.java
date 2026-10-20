package com.verdict.castcourt.domain.account.dto;

public class SignupDTO {
    private String username;
    private String password;
    private String nickname;

    public SignupDTO() {}

    public SignupDTO(String username, String password, String nickname, String authority) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
