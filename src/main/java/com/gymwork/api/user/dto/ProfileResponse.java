package com.gymwork.api.user.dto;

import com.gymwork.api.user.UserProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID id, String name, String email, BigDecimal heightCm, LocalDate birthDate, Instant createdAt) {

    public static ProfileResponse from(UserProfile profile) {
        var user = profile.getUser();
        return new ProfileResponse(
                user.getId(), user.getName(), user.getEmail(),
                profile.getHeightCm(), profile.getBirthDate(), user.getCreatedAt());
    }
}
