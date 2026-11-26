package com.gymwork.api.training;

import com.gymwork.api.common.domain.BaseEntity;
import com.gymwork.api.exercise.Exercise;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Exercício de um dia de treino, com a configuração planejada (A3). Carga sempre em kg. */
@Entity
@Table(name = "planned_exercises")
public class PlannedExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_day_id", nullable = false, updatable = false)
    private WorkoutDay day;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int sets;

    @Column(name = "reps_min", nullable = false)
    private int repsMin;

    @Column(name = "reps_max", nullable = false)
    private int repsMax;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(name = "suggested_load_kg", precision = 6, scale = 2)
    private BigDecimal suggestedLoadKg;

    @Column(name = "target_rir")
    private Integer targetRir;

    @Column(length = 2000)
    private String notes;

    protected PlannedExercise() {
    }

    public PlannedExercise(WorkoutDay day, Exercise exercise, int position) {
        this.day = day;
        this.userId = day.getUserId();
        this.exercise = exercise;
        this.position = position;
    }

    public void configure(PlannedExerciseConfig config) {
        this.sets = config.sets();
        this.repsMin = config.repsMin();
        this.repsMax = config.repsMax();
        this.restSeconds = config.restSeconds();
        this.suggestedLoadKg = config.suggestedLoadKg();
        this.targetRir = config.targetRir();
        this.notes = config.notes();
    }

    public PlannedExerciseConfig config() {
        return new PlannedExerciseConfig(sets, repsMin, repsMax, restSeconds, suggestedLoadKg, targetRir, notes);
    }

    /** Troca o exercício mantendo séries, repetições, descanso, carga e RIR. */
    void replaceExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public WorkoutDay getDay() {
        return day;
    }

    public UUID getUserId() {
        return userId;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public int getPosition() {
        return position;
    }

    public int getSets() {
        return sets;
    }

    public int getRepsMin() {
        return repsMin;
    }

    public int getRepsMax() {
        return repsMax;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public BigDecimal getSuggestedLoadKg() {
        return suggestedLoadKg;
    }

    public Integer getTargetRir() {
        return targetRir;
    }

    public String getNotes() {
        return notes;
    }
}
