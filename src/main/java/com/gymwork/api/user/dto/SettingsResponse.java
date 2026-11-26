package com.gymwork.api.user.dto;

import com.gymwork.api.user.LengthUnit;
import com.gymwork.api.user.ThemePreference;
import com.gymwork.api.user.UserProfile;
import com.gymwork.api.user.WeightUnit;

public record SettingsResponse(
        WeightUnit weightUnit,
        LengthUnit lengthUnit,
        ThemePreference theme,
        int defaultRestSeconds,
        boolean restSoundEnabled,
        String timezone) {

    public static SettingsResponse from(UserProfile profile) {
        return new SettingsResponse(
                profile.getWeightUnit(), profile.getLengthUnit(), profile.getTheme(),
                profile.getDefaultRestSeconds(), profile.isRestSoundEnabled(), profile.getTimezone());
    }
}
