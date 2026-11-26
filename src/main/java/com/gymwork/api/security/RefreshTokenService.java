package com.gymwork.api.security;

import com.gymwork.api.common.exception.InvalidRefreshTokenException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens com rotação: cada renovação invalida o token usado e emite um novo na mesma
 * família. Se um token já trocado aparecer de novo (fora da janela de tolerância), assume-se que
 * vazou e a família inteira é revogada — o atacante e o usuário legítimo precisam logar de novo.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record Rotation(UUID userId, IssuedRefreshToken token) {
    }

    /** Inicia uma nova sessão (nova família de tokens). */
    @Transactional
    public IssuedRefreshToken issue(UUID userId, String userAgent) {
        return create(userId, UUID.randomUUID(), userAgent).issued();
    }

    /**
     * Troca o token por um novo. Não desfaz a transação ao lançar {@link InvalidRefreshTokenException}
     * para que a revogação da família (detecção de reuso) seja gravada.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken, String userAgent) {
        RefreshToken current = repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            if (!current.wasRotatedWithin(now, properties.refreshReuseGrace())) {
                repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Reuso de refresh token detectado; sessão revogada (usuário {})", current.getUserId());
            }
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        Created next = create(current.getUserId(), current.getFamilyId(), userAgent);
        current.rotateTo(next.entity().getId(), now);
        return new Rotation(current.getUserId(), next.issued());
    }

    /** Logout: encerra a sessão (família) do token informado. Token desconhecido é ignorado. */
    @Transactional
    public void revokeSession(String rawToken) {
        repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Encerra todas as sessões do usuário (troca/redefinição de senha). */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllByUserId(userId, clock.instant());
    }

    private Created create(UUID userId, UUID familyId, String userAgent) {
        String raw = OpaqueTokens.generate();
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        RefreshToken entity = repository.save(
                new RefreshToken(userId, familyId, OpaqueTokens.hash(raw), expiresAt, userAgent));
        return new Created(entity, new IssuedRefreshToken(raw, expiresAt));
    }

    private record Created(RefreshToken entity, IssuedRefreshToken issued) {
    }
}
