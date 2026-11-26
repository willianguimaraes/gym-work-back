package com.gymwork.api.auth.dto;

import com.gymwork.api.user.dto.UserResponse;

/** Corpo devolvido no login/cadastro/renovação. O refresh token vai em cookie, nunca no corpo. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
