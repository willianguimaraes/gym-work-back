package com.gymwork.api.progression;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Ajustes do usuário na regra de progressão de um exercício (A12). */
@Entity
@Table(name = "progression_rules")
public class ProgressionRule extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false, updatable = false)
    private UUID exerciseId;

    @Column(name = "min_rir")
    private Integer minRir;

    @Column(name = "sessions_at_top", nullable = false)
    private int sessionsAtTop = 1;

    @Column(name = "load_increment_kg", precision = 5, scale = 2)
    private BigDecimal loadIncrementKg;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ProgressionRule() {
    }

    public ProgressionRule(UUID userId, UUID exerciseId) {
        this.userId = userId;
        this.exerciseId = exerciseId;
    }

    void update(RuleSettings settings) {
        this.minRir = settings.minRir();
        this.sessionsAtTop = settings.sessionsAtTop();
        this.loadIncrementKg = settings.loadIncrementKg();
        this.enabled = settings.enabled();
    }

    RuleSettings settings() {
        return new RuleSettings(minRir, sessionsAtTop, loadIncrementKg, enabled);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }
}
