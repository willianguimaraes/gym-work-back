package com.gymwork.api.security;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.exception.InvalidRefreshTokenException;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import com.gymwork.api.security.RefreshTokenService.Rotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                Duration.ofDays(30), Duration.ofSeconds(10), true, Duration.ofMinutes(30), "http://localhost");
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueStoresOnlyTheHashAndExpiresInThirtyDays() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        IssuedRefreshToken issued = service.issue(USER_ID, "Firefox");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(OpaqueTokens.hash(issued.value()))
                .isNotEqualTo(issued.value());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void rotateRevokesCurrentAndIssuesNewTokenInSameFamily() {
        RefreshToken current = stored("token-atual", NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(OpaqueTokens.hash("token-atual"))).thenReturn(Optional.of(current));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        Rotation rotation = service.rotate("token-atual", "Firefox");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(rotation.userId()).isEqualTo(USER_ID);
        assertThat(rotation.token().value()).isNotEqualTo("token-atual");
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedById()).isEqualTo(saved.getValue().getId());
    }

    @Test
    void reuseOfRotatedTokenRevokesTheWholeFamily() {
        RefreshToken stolen = stored("token-roubado", NOW.plus(Duration.ofDays(1)));
        stolen.rotateTo(UUID.randomUUID(), NOW.minus(Duration.ofMinutes(5)));
        when(repository.findByTokenHash(OpaqueTokens.hash("token-roubado"))).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> service.rotate("token-roubado", null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeFamily(FAMILY_ID, NOW);
    }

    @Test
    void reuseWithinGraceWindowIsRejectedWithoutRevokingFamily() {
        RefreshToken justRotated = stored("token-de-outra-aba", NOW.plus(Duration.ofDays(1)));
        justRotated.rotateTo(UUID.randomUUID(), NOW.minus(Duration.ofSeconds(3)));
        when(repository.findByTokenHash(OpaqueTokens.hash("token-de-outra-aba"))).thenReturn(Optional.of(justRotated));

        assertThatThrownBy(() -> service.rotate("token-de-outra-aba", null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken expired = stored("token-expirado", NOW.minus(Duration.ofSeconds(1)));
        when(repository.findByTokenHash(OpaqueTokens.hash("token-expirado"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("token-expirado", null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("desconhecido", null))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeSessionRevokesTheTokenFamily() {
        RefreshToken current = stored("token-atual", NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(OpaqueTokens.hash("token-atual"))).thenReturn(Optional.of(current));

        service.revokeSession("token-atual");

        verify(repository).revokeFamily(FAMILY_ID, NOW);
    }

    private static RefreshToken stored(String raw, Instant expiresAt) {
        return withId(new RefreshToken(USER_ID, FAMILY_ID, OpaqueTokens.hash(raw), expiresAt, null));
    }
}
