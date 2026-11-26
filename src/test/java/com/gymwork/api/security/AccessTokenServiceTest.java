package com.gymwork.api.security;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymwork.api.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class AccessTokenServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("test-secret-with-at-least-32-characters!", Duration.ofMinutes(15), "gym-work-api");

    private final JwtConfig config = new JwtConfig(PROPERTIES);

    @Test
    void generatedTokenIsValidAndCarriesTheUserIdAsSubject() {
        UUID id = UUID.randomUUID();
        AccessTokenService service = new AccessTokenService(config.jwtEncoder(), PROPERTIES, Clock.systemUTC());

        Jwt jwt = config.jwtDecoder().decode(service.generate(user(id)));

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana");
        assertThat(service.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = new AccessTokenService(config.jwtEncoder(), PROPERTIES, past).generate(user(UUID.randomUUID()));

        assertThatThrownBy(() -> config.jwtDecoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties other =
                new JwtProperties("another-secret-with-at-least-32-chars!!", Duration.ofMinutes(15), "gym-work-api");
        JwtConfig otherConfig = new JwtConfig(other);
        String foreign = new AccessTokenService(otherConfig.jwtEncoder(), other, Clock.systemUTC())
                .generate(user(UUID.randomUUID()));

        assertThatThrownBy(() -> config.jwtDecoder().decode(foreign)).isInstanceOf(JwtException.class);
    }

    private static User user(UUID id) {
        return withId(new User("Ana", "ana@gym.com", "hash"), id);
    }
}
