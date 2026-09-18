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

        UserSession registered = authService.register("alice", "correct horse battery9");
        UserSession loggedIn = authService.login("alice", "correct horse battery9");

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

        authService.register("alice", "secret-pass99");

        // 重复用户名：即使新密码合法也要拒绝（先查重）。
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
            () -> authService.register("alice", "another-pass99"));
        assertEquals("username already exists", dup.getMessage());
    }

    @Test
    void rejectsShortOrRestrictedUsernamesAndWeakPasswords() {
        AuthService authService = new AuthService(
            new InMemoryUserRepository(),
            new InMemoryAuthSessionRepository(),
            PasswordHasher.bcrypt(),
            clock
        );

        // BE-03：长度过短/非法字符/敏感词均拒绝。
        assertThrows(IllegalArgumentException.class, () -> authService.register("ab", "GoodPass9"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("name with space", "GoodPass9"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("admin", "GoodPass9"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("bad f**k", "GoodPass9"));

        // BE-04：弱密码/纯字母/过短均拒绝。
        assertThrows(IllegalArgumentException.class, () -> authService.register("gooduser", "short"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("gooduser", "onlyletters"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("gooduser", "password123"));

        // 合法用户名+强密码可通过；登录失败语句仍稳定。
        UserSession ok = authService.register("good_user", "Passw0rd123!");
        assertEquals("good_user", ok.user().username());
    }
}
