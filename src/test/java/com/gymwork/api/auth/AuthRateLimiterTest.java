package com.gymwork.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymwork.api.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-26T12:00:00Z"));
    private final AuthRateLimiter limiter = new AuthRateLimiter(clock);

    @Test
    void blocksLoginAfterTooManyWrongPasswordsAndReleasesAfterTheWindow() {
        for (int i = 0; i < AuthRateLimiter.LOGIN_PER_ACCOUNT; i++) {
            limiter.checkLogin("1.1.1.1", "Ana@Gym.test");
            limiter.loginFailed("1.1.1.1", "ana@gym.test");
        }
        assertThatThrownBy(() -> limiter.checkLogin("1.1.1.1", " ANA@gym.test "))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("Muitas tentativas. Tente de novo em 15 minutos.")
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));
        // outra conta no mesmo IP continua liberada
        assertThatCode(() -> limiter.checkLogin("1.1.1.1", "bia@gym.test")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> limiter.checkLogin("1.1.1.1", "ana@gym.test")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsTheAccountCounter() {
        for (int i = 0; i < AuthRateLimiter.LOGIN_PER_ACCOUNT - 1; i++) {
            limiter.loginFailed("1.1.1.1", "ana@gym.test");
        }
        limiter.loginSucceeded("1.1.1.1", "ana@gym.test");
        limiter.loginFailed("1.1.1.1", "ana@gym.test");
        assertThatCode(() -> limiter.checkLogin("1.1.1.1", "ana@gym.test")).doesNotThrowAnyException();
    }

    @Test
    void limitsPasswordResetRequests() {
        for (int i = 0; i < AuthRateLimiter.RESET_PER_ACCOUNT; i++) {
            limiter.passwordReset("2.2.2.2", "ana@gym.test");
        }
        assertThatThrownBy(() -> limiter.passwordReset("2.2.2.2", "ana@gym.test"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.passwordReset("3.3.3.3", "ana@gym.test")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
