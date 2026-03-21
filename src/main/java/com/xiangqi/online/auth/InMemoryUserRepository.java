package com.xiangqi.online.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, UserRecord> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(normalize(username)));
    }

    @Override
    public Optional<UserRecord> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public UserRecord save(UserRecord user) {
        byId.put(user.id(), user);
        byUsername.put(normalize(user.username()), user);
        return user;
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
