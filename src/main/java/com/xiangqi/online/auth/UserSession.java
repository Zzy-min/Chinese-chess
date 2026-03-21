package com.xiangqi.online.auth;

import java.time.Instant;

public final class UserSession {
    private final AuthUser user;
    private final String token;
    private final Instant expiresAt;

    public UserSession(AuthUser user, String token, Instant expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public AuthUser user() {
        return user;
    }

    public String token() {
        return token;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
