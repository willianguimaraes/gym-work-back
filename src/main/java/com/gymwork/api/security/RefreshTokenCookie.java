package com.gymwork.api.security;

import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token: HttpOnly (JavaScript não lê), SameSite=Strict e restrito a /api/auth,
 * então só é enviado nas chamadas de autenticação.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "gw_refresh";
    private static final String PATH = "/api/auth";

    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenCookie(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ResponseCookie create(IssuedRefreshToken token) {
        Duration maxAge = Duration.between(clock.instant(), token.expiresAt());
        return base(token.value()).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
