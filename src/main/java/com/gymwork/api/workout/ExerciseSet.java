package com.gymwork.api.workout;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Série registrada. Só existe se o usuário registrou: ausência de registro nunca é série feita. */
@Entity
@Table(name = "exercise_sets")
public class ExerciseSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_exercise_id", nullable = false, updatable = false)
    private SessionExercise sessionExercise;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "client_id", updatable = false)
    private UUID clientId;

    @Column(name = "set_number", nullable = false, updatable = false)
    private int setNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "set_type", nullable = false, length = 20)
    private SetType setType;

    @Column(name = "parent_set_id")
    private UUID parentSetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SetStatus status;

    @Column(name = "load_kg", precision = 6, scale = 2)
    private BigDecimal loadKg;

    private Integer reps;

    private Integer rir;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(length = 1000)
    private String notes;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected ExerciseSet() {
    }

    public ExerciseSet(SessionExercise sessionExercise, UUID clientId, int setNumber, Instant performedAt) {
        this.sessionExercise = sessionExercise;
        this.userId = sessionExercise.getUserId();
        this.clientId = clientId;
        this.setNumber = setNumber;
        this.performedAt = performedAt;
    }

    void record(SetData data) {
        this.setType = data.setType();
        this.parentSetId = data.parentSetId();
        this.status = data.status();
        this.loadKg = data.loadKg();
        this.reps = data.reps();
        this.rir = data.rir();
        this.restSeconds = data.restSeconds();
        this.notes = data.notes();
    }

    /** Conta como série realizada no progresso (trabalho ou adicional, concluída). */
    boolean countsAsCompletedSet() {
        return status == SetStatus.COMPLETED && setType.countsAsSet();
    }

    public SessionExercise getSessionExercise() {
        return sessionExercise;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public SetType getSetType() {
        return setType;
    }

    public UUID getParentSetId() {
        return parentSetId;
    }

    public SetStatus getStatus() {
        return status;
    }

    public BigDecimal getLoadKg() {
        return loadKg;
    }

    public Integer getReps() {
        return reps;
    }

    public Integer getRir() {
        return rir;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}
