package com.gymwork.api.auth;

import com.gymwork.api.auth.dto.AuthResponse;
import com.gymwork.api.security.AccessTokenService;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import com.gymwork.api.user.User;
import com.gymwork.api.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Monta a resposta de autenticação (JWT de acesso + dados do usuário + refresh token). */
@Component
public class SessionIssuer {

    private final AccessTokenService accessTokenService;

    public SessionIssuer(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    public AuthResult build(User user, IssuedRefreshToken refreshToken) {
        AuthResponse body = new AuthResponse(
                accessTokenService.generate(user),
                "Bearer",
                accessTokenService.expiresInSeconds(),
                UserResponse.from(user));
        return new AuthResult(body, refreshToken);
    }
}
