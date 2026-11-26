package com.gymwork.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.exception.EmailAlreadyInUseException;
import com.gymwork.api.common.exception.InvalidCredentialsException;
import com.gymwork.api.user.User;
import com.gymwork.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerNormalizesEmailHashesPasswordAndReturnsToken() {
        when(userRepository.existsByEmailIgnoreCase("ana@gym.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(tokenService.generate(any(User.class))).thenReturn("jwt-token");
        when(tokenService.expiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.register(new RegisterRequest(" Ana ", " Ana@Gym.com ", "senha1234"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@gym.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(1L);
    }

    @Test
    void registerRejectsDuplicatedEmail() {
        when(userRepository.existsByEmailIgnoreCase("ana@gym.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@gym.com", "senha1234")))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenWhenPasswordMatches() {
        User user = withId(new User("Ana", "ana@gym.com", "hash"), 1L);
        when(userRepository.findByEmailIgnoreCase("ana@gym.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha1234", "hash")).thenReturn(true);
        when(tokenService.generate(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("ana@gym.com", "senha1234"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = withId(new User("Ana", "ana@gym.com", "hash"), 1L);
        when(userRepository.findByEmailIgnoreCase("ana@gym.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@gym.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("x@gym.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@gym.com", "qualquer")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
