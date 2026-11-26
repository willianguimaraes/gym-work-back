package com.gymwork.api.workout;

import com.gymwork.api.common.domain.BaseEntity;
import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exercício executado numa sessão. Guarda uma cópia do nome e da configuração planejada, para que
 * editar o plano depois não altere o histórico.
 */
@Entity
@Table(name = "session_exercises")
public class SessionExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private WorkoutSession session;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, updatable = false)
    private Exercise exercise;

    @Column(name = "planned_exercise_id", updatable = false)
    private UUID plannedExerciseId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionExerciseStatus status = SessionExerciseStatus.PENDING;

    @Column(name = "substituted_by_id")
    private UUID substitutedById;

    @Column(length = 2000)
    private String notes;

    @Column(name = "exercise_name", nullable = false, length = 120, updatable = false)
    private String exerciseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Equipment equipment;

    @Column(name = "muscle_group", nullable = false, length = 30, updatable = false)
    private String muscleGroup;

    @Column(name = "planned_sets", updatable = false)
    private Integer plannedSets;

    @Column(name = "reps_min", updatable = false)
    private Integer repsMin;

    @Column(name = "reps_max", updatable = false)
    private Integer repsMax;

    @Column(name = "rest_seconds", updatable = false)
    private Integer restSeconds;

    @Column(name = "target_rir", updatable = false)
    private Integer targetRir;

    @Column(name = "reference_load_kg", precision = 6, scale = 2, updatable = false)
    private BigDecimal referenceLoadKg;

    protected SessionExercise() {
    }

    /** Exercício fora do plano (extra, avulso): sem configuração planejada. */
    public SessionExercise(WorkoutSession session, Exercise exercise, int position) {
        this.session = session;
        this.userId = session.getUserId();
        this.exercise = exercise;
        this.position = position;
        this.exerciseName = exercise.getName();
        this.equipment = exercise.getEquipment();
        this.muscleGroup = exercise.getPrimaryMuscleGroup();
    }

    /** Copia a configuração planejada (do plano ou do exercício substituído). */
    void copyPlan(UUID plannedExerciseId, PlannedSnapshot plan) {
        this.plannedExerciseId = plannedExerciseId;
        this.plannedSets = plan.sets();
        this.repsMin = plan.repsMin();
        this.repsMax = plan.repsMax();
        this.restSeconds = plan.restSeconds();
        this.targetRir = plan.targetRir();
        this.referenceLoadKg = plan.loadKg();
    }

    PlannedSnapshot plannedSnapshot() {
        return new PlannedSnapshot(plannedSets, repsMin, repsMax, restSeconds, targetRir, referenceLoadKg);
    }

    /**
     * Recalcula o status pelas séries válidas concluídas (trabalho e adicionais). Pular é desfeito
     * ao registrar uma série; substituído é definitivo.
     */
    void refreshStatus(long completedSets) {
        if (status == SessionExerciseStatus.SUBSTITUTED) {
            return;
        }
        if (completedSets == 0) {
            status = status == SessionExerciseStatus.SKIPPED ? SessionExerciseStatus.SKIPPED : SessionExerciseStatus.PENDING;
        } else if (plannedSets == null || completedSets >= plannedSets) {
            status = SessionExerciseStatus.DONE;
        } else {
            status = SessionExerciseStatus.PARTIAL;
        }
    }

    void skip() {
        status = SessionExerciseStatus.SKIPPED;
    }

    void unskip() {
        if (status == SessionExerciseStatus.SKIPPED) {
            status = SessionExerciseStatus.PENDING;
        }
    }

    void substitutedBy(SessionExercise replacement) {
        status = SessionExerciseStatus.SUBSTITUTED;
        substitutedById = replacement.getId();
    }

    void updateNotes(String notes) {
        this.notes = notes;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public WorkoutSession getSession() {
        return session;
    }

    public UUID getUserId() {
        return userId;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public UUID getPlannedExerciseId() {
        return plannedExerciseId;
    }

    public int getPosition() {
        return position;
    }

    public SessionExerciseStatus getStatus() {
        return status;
    }

    public UUID getSubstitutedById() {
        return substitutedById;
    }

    public String getNotes() {
        return notes;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public Integer getPlannedSets() {
        return plannedSets;
    }

    public Integer getRepsMin() {
        return repsMin;
    }

    public Integer getRepsMax() {
        return repsMax;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public Integer getTargetRir() {
        return targetRir;
    }

    public BigDecimal getReferenceLoadKg() {
        return referenceLoadKg;
    }
}
