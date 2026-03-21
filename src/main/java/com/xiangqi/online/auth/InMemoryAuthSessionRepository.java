package com.xiangqi.online.auth;

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
        return Optional.ofNullable(sessions.get(token));
    }

    @Override
    public void deleteByToken(String token) {
        sessions.remove(token);
    }
}
