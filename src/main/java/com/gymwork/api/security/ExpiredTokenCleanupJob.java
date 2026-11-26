package com.gymwork.api.security;

import com.gymwork.api.auth.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Remove diariamente tokens expirados há mais de 7 dias (mantidos um tempo para auditoria). */
@Component
public class ExpiredTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public ExpiredTokenCleanupJob(
            RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Instant limit = clock.instant().minus(RETENTION);
        int refresh = refreshTokens.deleteExpiredBefore(limit);
        int reset = resetTokens.deleteExpiredBefore(limit);
        log.info("Limpeza de tokens expirados: {} refresh, {} redefinição de senha", refresh, reset);
    }
}
