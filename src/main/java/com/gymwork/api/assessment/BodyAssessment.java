package com.gymwork.api.assessment;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Avaliação física (independente do planejamento). Todos os valores são opcionais: o que não foi
 * medido não existe, e nada é estimado como se fosse medição.
 */
@Entity
@Table(name = "body_assessments")
public class BodyAssessment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(length = 2000)
    private String notes;

    protected BodyAssessment() {
    }

    public BodyAssessment(UUID userId) {
        this.userId = userId;
    }

    void update(LocalDate assessedOn, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bodyFatPct, String notes) {
        this.assessedOn = assessedOn;
        this.weightKg = scale(weightKg, 2);
        this.heightCm = scale(heightCm, 1);
        this.bodyFatPct = scale(bodyFatPct, 1);
        this.notes = notes;
    }

    static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getAssessedOn() {
        return assessedOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public String getNotes() {
        return notes;
    }
}
