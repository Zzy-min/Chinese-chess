package com.xiangqi.online.auth;

import java.util.Optional;

public interface UserRepository {
    Optional<UserRecord> findByUsername(String username);

    Optional<UserRecord> findById(String id);

    UserRecord save(UserRecord user);
}
