package com.gymwork.api.user.dto;

import com.gymwork.api.user.LengthUnit;
import com.gymwork.api.user.ThemePreference;
import com.gymwork.api.user.WeightUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSettingsRequest(
        @NotNull WeightUnit weightUnit,
        @NotNull LengthUnit lengthUnit,
        @NotNull ThemePreference theme,
        @NotNull @Min(0) @Max(900) Integer defaultRestSeconds,
        @NotNull Boolean restSoundEnabled,
        @NotBlank String timezone) {
}
