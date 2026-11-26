package com.gymwork.api.user;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Data e fuso horário do ponto de vista do usuário (preferência "fuso horário" do perfil). */
@Component
public class UserTime {

    private final UserProfileRepository profileRepository;
    private final Clock clock;

    public UserTime(UserProfileRepository profileRepository, Clock clock) {
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ZoneId zone(UUID userId) {
        String timezone = profileRepository.findById(userId)
                .map(UserProfile::getTimezone)
                .orElse(UserProfile.DEFAULT_TIMEZONE);
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneId.of(UserProfile.DEFAULT_TIMEZONE);
        }
    }

    /** Unidade de peso preferida (relatórios em texto); o banco guarda sempre em kg. */
    @Transactional(readOnly = true)
    public WeightUnit weightUnit(UUID userId) {
        return profileRepository.findById(userId).map(UserProfile::getWeightUnit).orElse(WeightUnit.KG);
    }

    /** "Hoje" no fuso do usuário. */
    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(zone(userId)));
    }
}
