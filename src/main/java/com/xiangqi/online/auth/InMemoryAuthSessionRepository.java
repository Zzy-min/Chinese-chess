package com.xiangqi.online.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuthSessionRepository implements AuthSessionRepository {
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    public UserSession save(UserSession session) {
        sessions.put(session.token(), session);
        return session;
    }

    @Override
    public Optional<UserSession> findByToken(String token) {
        UserSession session = sessions.get(token);
        if (session != null && session.expiresAt().isAfter(Instant.now())) {
            return Optional.of(session);
        }
        if (session != null) {
            sessions.remove(token);
        }
        return Optional.empty();
    }

    @Override
    public void deleteByToken(String token) {
        sessions.remove(token);
    }
}
