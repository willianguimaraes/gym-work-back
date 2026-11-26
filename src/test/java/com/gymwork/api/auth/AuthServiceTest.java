package com.gymwork.api.auth;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.auth.dto.LoginRequest;
import com.gymwork.api.auth.dto.RegisterRequest;
import com.gymwork.api.common.exception.EmailAlreadyInUseException;
import com.gymwork.api.common.exception.InvalidCredentialsException;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import com.gymwork.api.security.RefreshTokenService.Rotation;
import com.gymwork.api.user.User;
import com.gymwork.api.user.UserProfile;
import com.gymwork.api.user.UserProfileRepository;
import com.gymwork.api.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final IssuedRefreshToken REFRESH = new IssuedRefreshToken("refresh", Instant.now());

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SessionIssuer sessionIssuer;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerNormalizesEmailHashesPasswordCreatesProfileAndStartsSession() {
        AuthResult expected = mock(AuthResult.class);
        when(userRepository.existsByEmail("ana@gym.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(refreshTokenService.issue(any(), eq("Firefox"))).thenReturn(REFRESH);
        when(sessionIssuer.build(any(User.class), eq(REFRESH))).thenReturn(expected);

        AuthResult result = authService.register(new RegisterRequest(" Ana ", " Ana@Gym.com ", "senha1234"), "Firefox");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@gym.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        verify(profileRepository).save(any(UserProfile.class));
        assertThat(result).isSameAs(expected);
    }

    @Test
    void registerRejectsDuplicatedEmail() {
        when(userRepository.existsByEmail("ana@gym.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@gym.com", "senha1234"), null))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginStartsSessionWhenPasswordMatches() {
        User user = withId(new User("Ana", "ana@gym.com", "hash"));
        when(userRepository.findByEmail("ana@gym.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha1234", "hash")).thenReturn(true);
        when(refreshTokenService.issue(user.getId(), null)).thenReturn(REFRESH);

        authService.login(new LoginRequest("ANA@gym.com", "senha1234"), null);

        verify(sessionIssuer).build(user, REFRESH);
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = withId(new User("Ana", "ana@gym.com", "hash"));
        when(userRepository.findByEmail("ana@gym.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@gym.com", "errada"), null))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void loginFailsWhenUserDoesNotExist() {
        when(userRepository.findByEmail("x@gym.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@gym.com", "qualquer"), null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenAndBuildsNewSession() {
        User user = withId(new User("Ana", "ana@gym.com", "hash"));
        when(refreshTokenService.rotate("antigo", null)).thenReturn(new Rotation(user.getId(), REFRESH));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.refresh("antigo", null);

        verify(sessionIssuer).build(user, REFRESH);
    }

    @Test
    void logoutRevokesTheSession() {
        authService.logout("token");

        verify(refreshTokenService).revokeSession("token");
    }

    @Test
    void registerUsesGeneratedUserIdForSession() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), id));

        authService.register(new RegisterRequest("Ana", "ana@gym.com", "senha1234"), null);

        verify(refreshTokenService).issue(id, null);
    }
}
