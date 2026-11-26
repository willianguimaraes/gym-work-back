package com.gymwork.api.auth;

import com.gymwork.api.auth.dto.AuthResponse;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;

/** Resultado de uma autenticação: o corpo da resposta e o refresh token que vai para o cookie. */
public record AuthResult(AuthResponse response, IssuedRefreshToken refreshToken) {
}
