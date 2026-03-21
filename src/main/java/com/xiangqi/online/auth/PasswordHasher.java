package com.xiangqi.online.auth;

import org.mindrot.jbcrypt.BCrypt;

public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    static PasswordHasher bcrypt() {
        return new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
            }

            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                return BCrypt.checkpw(rawPassword, passwordHash);
            }
        };
    }
}
