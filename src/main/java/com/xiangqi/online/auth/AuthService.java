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
        // BE-03：长度上下限 + 字符集白名单（字母数字中文与有限符号）。
        if (value.length() < 3 || value.length() > 32) {
            throw new IllegalArgumentException("用户名长度需在 3 到 32 个字符之间");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
            if (!allowed) {
                throw new IllegalArgumentException("用户名只能包含字母、数字、中文、下划线、短横线或点");
            }
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("用户名不能包含控制字符");
            }
        }
        // 敏感词黑名单（简单内置贝，运维可扩展为配置文件）。
        if (containsBannedWord(value)) {
            throw new IllegalArgumentException("用户名包含不允许的内容");
        }
        return value;
    }

    private boolean containsBannedWord(String value) {
        // 示例黑名单：粗口/辱骂/系统保留词。后续可外置到配置。
        String[] banned = {"admin", "root", "fuck", "shit", "bitch", "asshole", "stupid", "性交", "淫", "屎", "弱智", "sb"};
        for (String word : banned) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("密码至少需要 8 位");
        }
        if (rawPassword.length() > 128) {
            throw new IllegalArgumentException("密码过长");
        }
        // BE-04：复杂度——至少包含字母和数字。
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码需同时包含字母和数字");
        }
        // 常见/弱密码黑名单。
        String lowercase = rawPassword.toLowerCase();
        String[] weak = {"password", "password123", "12345678", "123456789", "qwerty123", "abcdefgh", "letmein", "11111111", "aaaaaaaa"};
        for (String w : weak) {
            if (lowercase.equals(w)) {
                throw new IllegalArgumentException("密码过于常见，请换一个更难猜的");
            }
        }
    }
}
