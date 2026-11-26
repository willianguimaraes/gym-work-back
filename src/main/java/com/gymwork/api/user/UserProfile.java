package com.gymwork.api.user;

import com.gymwork.api.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Dados pessoais e preferências do usuário (1:1 com {@link User}, mesma chave). */
@Entity
@Table(name = "user_profiles")
public class UserProfile extends AuditableEntity {

    public static final int DEFAULT_REST_SECONDS = 90;
    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "height_cm", precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_unit", nullable = false, length = 2)
    private WeightUnit weightUnit = WeightUnit.KG;

    @Enumerated(EnumType.STRING)
    @Column(name = "length_unit", nullable = false, length = 2)
    private LengthUnit lengthUnit = LengthUnit.CM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ThemePreference theme = ThemePreference.DARK;

    @Column(name = "default_rest_seconds", nullable = false)
    private int defaultRestSeconds = DEFAULT_REST_SECONDS;

    @Column(name = "rest_sound_enabled", nullable = false)
    private boolean restSoundEnabled = true;

    @Column(nullable = false, length = 64)
    private String timezone = DEFAULT_TIMEZONE;

    protected UserProfile() {
    }

    public UserProfile(User user) {
        this.user = user;
    }

    public void updatePersonalData(BigDecimal heightCm, LocalDate birthDate) {
        this.heightCm = heightCm;
        this.birthDate = birthDate;
    }

    public void updateSettings(
            WeightUnit weightUnit,
            LengthUnit lengthUnit,
            ThemePreference theme,
            int defaultRestSeconds,
            boolean restSoundEnabled,
            String timezone) {
        this.weightUnit = weightUnit;
        this.lengthUnit = lengthUnit;
        this.theme = theme;
        this.defaultRestSeconds = defaultRestSeconds;
        this.restSoundEnabled = restSoundEnabled;
        this.timezone = timezone;
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public WeightUnit getWeightUnit() {
        return weightUnit;
    }

    public LengthUnit getLengthUnit() {
        return lengthUnit;
    }

    public ThemePreference getTheme() {
        return theme;
    }

    public int getDefaultRestSeconds() {
        return defaultRestSeconds;
    }

    public boolean isRestSoundEnabled() {
        return restSoundEnabled;
    }

    public String getTimezone() {
        return timezone;
    }
}
