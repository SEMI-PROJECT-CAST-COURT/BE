package com.verdict.castcourt.domain.account.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "account")
public class AccountEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "authority")
    private String authority;

    @Column(name = "is_usage")
    private boolean isUsage;

    public AccountEntity() {}

    public AccountEntity(int id, String username, String password, String nickname, String authority, boolean isUsage) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.authority = authority;
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

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public boolean isUsage() {
        return isUsage;
    }
    public void setUsage(boolean usage) {
        isUsage = usage;
    }
}
