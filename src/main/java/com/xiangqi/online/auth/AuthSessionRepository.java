package com.xiangqi.online.auth;

import java.util.Optional;

public interface AuthSessionRepository {
    UserSession save(UserSession session);

    Optional<UserSession> findByToken(String token);

    default void deleteByToken(String token) {
    }
}
