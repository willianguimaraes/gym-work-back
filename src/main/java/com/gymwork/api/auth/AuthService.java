package com.gymwork.api.auth;

import com.gymwork.api.auth.dto.LoginRequest;
import com.gymwork.api.auth.dto.RegisterRequest;
import com.gymwork.api.common.exception.EmailAlreadyInUseException;
import com.gymwork.api.common.exception.InvalidCredentialsException;
import com.gymwork.api.common.exception.InvalidRefreshTokenException;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.security.RefreshTokenService.Rotation;
import com.gymwork.api.user.Emails;
import com.gymwork.api.user.User;
import com.gymwork.api.user.UserProfile;
import com.gymwork.api.user.UserProfileRepository;
import com.gymwork.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SessionIssuer sessionIssuer;

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            SessionIssuer sessionIssuer) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.sessionIssuer = sessionIssuer;
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String userAgent) {
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }
        User user = userRepository.save(
                new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        profileRepository.save(new UserProfile(user));
        return sessionIssuer.build(user, refreshTokenService.issue(user.getId(), userAgent));
    }

    @Transactional
    public AuthResult login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmail(Emails.normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return sessionIssuer.build(user, refreshTokenService.issue(user.getId(), userAgent));
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        Rotation rotation = refreshTokenService.rotate(rawRefreshToken, userAgent);
        User user = userRepository.findById(rotation.userId()).orElseThrow(InvalidRefreshTokenException::new);
        return sessionIssuer.build(user, rotation.token());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeSession(rawRefreshToken);
    }
}
