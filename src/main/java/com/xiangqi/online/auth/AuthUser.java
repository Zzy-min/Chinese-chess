package com.xiangqi.online.auth;

public final class AuthUser {
    private final String id;
    private final String username;

    public AuthUser(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String id() {
        return id;
    }

    public String username() {
        return username;
    }
}
