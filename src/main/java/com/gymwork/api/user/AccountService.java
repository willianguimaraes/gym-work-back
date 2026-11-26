package com.gymwork.api.user;

import com.gymwork.api.auth.AuthResult;
import com.gymwork.api.auth.PasswordResetTokenRepository;
import com.gymwork.api.auth.SessionIssuer;
import com.gymwork.api.common.exception.AuthenticatedUserNotFoundException;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.security.RefreshTokenRepository;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.user.dto.ChangePasswordRequest;
import com.gymwork.api.user.dto.ProfileResponse;
import com.gymwork.api.user.dto.SettingsResponse;
import com.gymwork.api.user.dto.UpdateProfileRequest;
import com.gymwork.api.user.dto.UpdateSettingsRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações do usuário sobre a própria conta. Todo método recebe o ID do usuário autenticado. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final SessionIssuer sessionIssuer;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<UserDataDeletionHandler> deletionHandlers;

    public AccountService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            RefreshTokenService refreshTokenService,
            SessionIssuer sessionIssuer,
            PasswordEncoder passwordEncoder,
            ObjectProvider<UserDataDeletionHandler> deletionHandlers) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.sessionIssuer = sessionIssuer;
        this.passwordEncoder = passwordEncoder;
        this.deletionHandlers = deletionHandlers;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return ProfileResponse.from(profile(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = profile(userId);
        profile.getUser().rename(request.name().trim());
        profile.updatePersonalData(request.heightCm(), request.birthDate());
        return ProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings(UUID userId) {
        return SettingsResponse.from(profile(userId));
    }

    @Transactional
    public SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request) {
        UserProfile profile = profile(userId);
        profile.updateSettings(
                request.weightUnit(),
                request.lengthUnit(),
                request.theme(),
                request.defaultRestSeconds(),
                request.restSoundEnabled(),
                validTimezone(request.timezone()));
        return SettingsResponse.from(profile);
    }

    /** Troca a senha, encerra todas as sessões e abre uma nova para o dispositivo atual. */
    @Transactional
    public AuthResult changePassword(UUID userId, ChangePasswordRequest request, String userAgent) {
        User user = user(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidFieldException("currentPassword", "Senha atual incorreta");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId);
        return sessionIssuer.build(user, refreshTokenService.issue(userId, userAgent));
    }

    /** Exclui a conta e todos os dados do usuário, em uma única transação. */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = user(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidFieldException("password", "Senha incorreta");
        }
        deletionHandlers.orderedStream().forEach(handler -> handler.deleteUserData(userId));
        refreshTokenRepository.deleteAllByUserId(userId);
        resetTokenRepository.deleteAllByUserId(userId);
        profileRepository.deleteById(userId);
        userRepository.delete(user);
        log.info("Conta excluída: {}", userId);
    }

    private User user(UUID userId) {
        return userRepository.findById(userId).orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private UserProfile profile(UUID userId) {
        return profileRepository.findWithUser(userId).orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private static String validTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new InvalidFieldException("timezone", "Fuso horário inválido");
        }
    }
}
