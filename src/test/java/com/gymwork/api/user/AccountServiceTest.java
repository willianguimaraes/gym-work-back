package com.gymwork.api.user;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymwork.api.auth.PasswordResetTokenRepository;
import com.gymwork.api.auth.SessionIssuer;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.security.RefreshTokenRepository;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import com.gymwork.api.user.dto.ChangePasswordRequest;
import com.gymwork.api.user.dto.SettingsResponse;
import com.gymwork.api.user.dto.UpdateSettingsRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SessionIssuer sessionIssuer;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ObjectProvider<UserDataDeletionHandler> deletionHandlers;

    @Mock
    private UserDataDeletionHandler trainingDataHandler;

    @InjectMocks
    private AccountService accountService;

    private final User user = withId(new User("Ana", "ana@gym.com", "hash"));

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithoutChangingAnything() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(
                        user.getId(), new ChangePasswordRequest("errada", "nova-senha-123"), null))
                .isInstanceOf(InvalidFieldException.class)
                .extracting("field").isEqualTo("currentPassword");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordEndsAllSessionsBeforeOpeningANewOne() {
        IssuedRefreshToken refresh = new IssuedRefreshToken("novo", Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("atual", "hash")).thenReturn(true);
        when(passwordEncoder.encode("nova-senha-123")).thenReturn("hash-novo");
        when(refreshTokenService.issue(user.getId(), null)).thenReturn(refresh);

        accountService.changePassword(user.getId(), new ChangePasswordRequest("atual", "nova-senha-123"), null);

        assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
        InOrder order = inOrder(refreshTokenService, sessionIssuer);
        order.verify(refreshTokenService).revokeAll(user.getId());
        order.verify(refreshTokenService).issue(user.getId(), null);
        order.verify(sessionIssuer).build(user, refresh);
    }

    @Test
    void deleteAccountRequiresCorrectPassword() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.deleteAccount(user.getId(), "errada"))
                .isInstanceOf(InvalidFieldException.class);
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(deletionHandlers);
    }

    @Test
    void deleteAccountRemovesModuleDataBeforeTheUser() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(deletionHandlers.orderedStream()).thenReturn(Stream.of(trainingDataHandler));

        accountService.deleteAccount(user.getId(), "senha");

        InOrder order = inOrder(trainingDataHandler, refreshTokenRepository, profileRepository, userRepository);
        order.verify(trainingDataHandler).deleteUserData(user.getId());
        order.verify(refreshTokenRepository).deleteAllByUserId(user.getId());
        order.verify(profileRepository).deleteById(user.getId());
        order.verify(userRepository).delete(user);
    }

    @Test
    void updateSettingsRejectsUnknownTimezone() {
        UserProfile profile = new UserProfile(user);
        when(profileRepository.findWithUser(user.getId())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> accountService.updateSettings(user.getId(), settings("Marte/Olympus")))
                .isInstanceOf(InvalidFieldException.class)
                .extracting("field").isEqualTo("timezone");
    }

    @Test
    void updateSettingsAppliesAllPreferences() {
        UserProfile profile = new UserProfile(user);
        when(profileRepository.findWithUser(user.getId())).thenReturn(Optional.of(profile));

        SettingsResponse response = accountService.updateSettings(user.getId(), settings("Europe/Lisbon"));

        assertThat(response.weightUnit()).isEqualTo(WeightUnit.LB);
        assertThat(response.theme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(response.defaultRestSeconds()).isEqualTo(120);
        assertThat(response.timezone()).isEqualTo("Europe/Lisbon");
    }

    private static UpdateSettingsRequest settings(String timezone) {
        return new UpdateSettingsRequest(WeightUnit.LB, LengthUnit.IN, ThemePreference.LIGHT, 120, false, timezone);
    }
}
