package com.xiangqi.online.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class AuthService {
    private static final long SESSION_DAYS = 14L;

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordHasher hasher;
    private final Clock clock;

    public AuthService(
        UserRepository users,
        AuthSessionRepository sessions,
        PasswordHasher hasher,
        Clock clock
    ) {
        this.users = Objects.requireNonNull(users, "users");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UserSession register(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        validatePassword(rawPassword);
        if (users.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("username already exists");
        }
        UserRecord user = new UserRecord(
            UUID.randomUUID().toString(),
            normalized,
            hasher.hash(rawPassword),
            Instant.now(clock)
        );
        users.save(user);
        return createSession(user);
    }

    public UserSession login(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        UserRecord user = users.findByUsername(normalized)
            .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!hasher.matches(rawPassword, user.passwordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return createSession(user);
    }

    private UserSession createSession(UserRecord user) {
        UserSession session = new UserSession(
            user.toAuthUser(),
            UUID.randomUUID().toString().replace("-", ""),
            Instant.now(clock).plus(SESSION_DAYS, ChronoUnit.DAYS)
        );
        sessions.save(session);
        return session;
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim().toLowerCase();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        return value;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
    }
}
