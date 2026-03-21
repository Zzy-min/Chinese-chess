package com.xiangqi.online;

import com.xiangqi.online.auth.AuthService;
import com.xiangqi.online.auth.InMemoryAuthSessionRepository;
import com.xiangqi.online.auth.InMemoryUserRepository;
import com.xiangqi.online.auth.PasswordHasher;
import com.xiangqi.online.auth.UserSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void registerAndLoginUseStableIdentityAndHashedPassword() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        AuthService authService = new AuthService(users, new InMemoryAuthSessionRepository(), PasswordHasher.bcrypt(), clock);

        UserSession registered = authService.register("alice", "correct horse battery staple");
        UserSession loggedIn = authService.login("alice", "correct horse battery staple");

        assertEquals("alice", registered.user().username());
        assertEquals(registered.user().id(), loggedIn.user().id());
        assertNotEquals(registered.token(), loggedIn.token());
        assertTrue(users.findByUsername("alice").orElseThrow().passwordHash().startsWith("$2"));
    }

    @Test
    void duplicateUsernameIsRejected() {
        AuthService authService = new AuthService(
            new InMemoryUserRepository(),
            new InMemoryAuthSessionRepository(),
            PasswordHasher.bcrypt(),
            clock
        );

        authService.register("alice", "secret-pass");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> authService.register("alice", "another-pass"));

        assertEquals("username already exists", error.getMessage());
    }
}
