package com.gymwork.api.workout;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Observação durante ou depois do treino (dor, sono, energia…). Só registra o relato do usuário:
 * o sistema não faz diagnóstico nem recomenda tratamento. Dor no exercício suspende sugestões de
 * aumento de carga na análise de progressão (Etapa 5).
 */
@Entity
@Table(name = "workout_observations")
public class WorkoutObservation extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "session_exercise_id")
    private UUID sessionExerciseId;

    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ObservationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_region", length = 20)
    private BodyRegion bodyRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_side", length = 10)
    private BodySide bodySide;

    private Integer intensity;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ObservationMoment moment;

    @Column(nullable = false)
    private boolean interrupted;

    @Column(length = 2000)
    private String text;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected WorkoutObservation() {
    }

    public WorkoutObservation(UUID userId) {
        this.userId = userId;
    }

    void update(ObservationData data) {
        this.sessionId = data.sessionId();
        this.sessionExerciseId = data.sessionExerciseId();
        this.exerciseId = data.exerciseId();
        this.category = data.category();
        this.bodyRegion = data.bodyRegion();
        this.bodySide = data.bodySide();
        this.intensity = data.intensity();
        this.moment = data.moment();
        this.interrupted = data.interrupted();
        this.text = data.text();
        this.observedAt = data.observedAt();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getSessionExerciseId() {
        return sessionExerciseId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public ObservationCategory getCategory() {
        return category;
    }

    public BodyRegion getBodyRegion() {
        return bodyRegion;
    }

    public BodySide getBodySide() {
        return bodySide;
    }

    public Integer getIntensity() {
        return intensity;
    }

    public ObservationMoment getMoment() {
        return moment;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public String getText() {
        return text;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
