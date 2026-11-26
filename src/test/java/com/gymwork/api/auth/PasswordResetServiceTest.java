package com.gymwork.api.auth;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.mail.MailService;
import com.gymwork.api.security.AuthProperties;
import com.gymwork.api.security.OpaqueTokens;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.user.User;
import com.gymwork.api.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private MailService mailService;

    private PasswordResetService service;
    private final User user = withId(new User("Ana", "ana@gym.com", "hash-antigo"));

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                Duration.ofDays(30), Duration.ofSeconds(10), true, Duration.ofMinutes(30), "http://localhost:5173/");
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, refreshTokenService,
                mailService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownEmailDoesNothingAndRevealsNothing() {
        when(userRepository.findByEmail("ninguem@gym.com")).thenReturn(Optional.empty());

        service.requestReset("Ninguem@gym.com");

        verifyNoInteractions(tokenRepository, mailService);
    }

    @Test
    void requestInvalidatesPreviousLinksAndEmailsTokenWhoseHashIsStored() {
        when(userRepository.findByEmail("ana@gym.com")).thenReturn(Optional.of(user));

        service.requestReset(" ana@gym.com ");

        verify(tokenRepository).invalidateAllByUserId(user.getId(), NOW);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("ana@gym.com"), anyString(), body.capture());

        Matcher link = TOKEN_IN_LINK.matcher(body.getValue());
        assertThat(link.find()).isTrue();
        assertThat(body.getValue()).contains("http://localhost:5173/redefinir-senha?token=");
        assertThat(OpaqueTokens.hash(link.group(1))).isEqualTo(
                org.springframework.test.util.ReflectionTestUtils.getField(saved.getValue(), "tokenHash"));
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void resetChangesPasswordMarksTokenUsedAndEndsAllSessions() {
        PasswordResetToken token = new PasswordResetToken(user.getId(), OpaqueTokens.hash("raw"), NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("raw"))).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("nova-senha-123")).thenReturn("hash-novo");

        service.resetPassword("raw", "nova-senha-123");

        assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(refreshTokenService).revokeAll(user.getId());
    }

    @Test
    void expiredTokenIsRejected() {
        PasswordResetToken token = new PasswordResetToken(user.getId(), OpaqueTokens.hash("raw"), NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw", "nova-senha-123"))
                .isInstanceOf(InvalidFieldException.class);
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void alreadyUsedTokenIsRejected() {
        PasswordResetToken token = new PasswordResetToken(user.getId(), OpaqueTokens.hash("raw"), NOW.plusSeconds(60));
        token.markUsed(NOW.minusSeconds(5));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw", "nova-senha-123"))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(PasswordResetService.INVALID_LINK);
    }
}
