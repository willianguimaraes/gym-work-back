package com.gymwork.api.auth;

import com.gymwork.api.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
