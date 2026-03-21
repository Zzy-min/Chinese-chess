package com.xiangqi.online.auth;

import java.time.Instant;

public final class UserRecord {
    private final String id;
    private final String username;
    private final String passwordHash;
    private final Instant createdAt;

    public UserRecord(String id, String username, String passwordHash, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AuthUser toAuthUser() {
        return new AuthUser(id, username);
    }
}
