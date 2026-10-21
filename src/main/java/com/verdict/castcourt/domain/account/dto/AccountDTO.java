package com.verdict.castcourt.domain.account.dto;

public class AccountDTO {
    private int id;
    private String username;
    private String nickname;
    private boolean isUsage;

    public AccountDTO() {}

    public AccountDTO(int id, String username, String nickname, boolean isUsage) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.isUsage = isUsage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isUsage() {
        return isUsage;
    }

    public void setUsage(boolean usage) {
        isUsage = usage;
    }
}
