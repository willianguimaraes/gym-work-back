package com.gymwork.api.training;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.UUID;

/** Dia de treino do modelo semanal da fase (ex.: "UPPER" na segunda-feira). */
@Entity
@Table(name = "workout_days")
public class WorkoutDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false, updatable = false)
    private TrainingPhase phase;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int position;

    @Column(length = 2000)
    private String notes;

    protected WorkoutDay() {
    }

    public WorkoutDay(TrainingPhase phase, int position) {
        this.phase = phase;
        this.userId = phase.getUserId();
        this.position = position;
    }

    public void update(String name, DayOfWeek dayOfWeek, String notes) {
        this.name = name;
        this.dayOfWeek = dayOfWeek;
        this.notes = notes;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public TrainingPhase getPhase() {
        return phase;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getPosition() {
        return position;
    }

    public String getNotes() {
        return notes;
    }
}
