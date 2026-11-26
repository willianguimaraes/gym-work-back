package com.gymwork.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymwork.api.config.JwtConfig;
import com.gymwork.api.config.JwtProperties;
import com.gymwork.api.user.User;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters!";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), "gym-work-api");
    private final JwtConfig config = new JwtConfig(properties);
    private final TokenService tokenService = new TokenService(config.jwtEncoder(), properties);
    private final JwtDecoder decoder = config.jwtDecoder();

    @Test
    void generatedTokenIsValidAndCarriesUserClaims() {
        User user = new User("Ana", "ana@gym.com", "hash");
        ReflectionTestUtils.setField(user, "id", 42L);

        Jwt jwt = decoder.decode(tokenService.generate(user));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@gym.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana");
        assertThat(tokenService.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties other = new JwtProperties("another-secret-with-at-least-32-chars!!", Duration.ofHours(1), "gym-work-api");
        JwtConfig otherConfig = new JwtConfig(other);
        User user = new User("Ana", "ana@gym.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        String foreignToken = new TokenService(otherConfig.jwtEncoder(), other).generate(user);

        assertThatThrownBy(() -> decoder.decode(foreignToken)).isInstanceOf(JwtException.class);
    }
}
