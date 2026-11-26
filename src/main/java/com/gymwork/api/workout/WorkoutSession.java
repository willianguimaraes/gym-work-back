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
 * Sessão de treino realizada. As referências ao plano são opcionais (treino avulso) e podem virar
 * nulas se o plano mudar; os nomes copiados no início (snapshot) preservam o histórico.
 */
@Entity
@Table(name = "workout_sessions")
public class WorkoutSession extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "week_id")
    private UUID weekId;

    @Column(name = "workout_day_id")
    private UUID workoutDayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "plan_name", length = 120, updatable = false)
    private String planName;

    @Column(name = "phase_name", length = 120, updatable = false)
    private String phaseName;

    @Column(name = "week_number", updatable = false)
    private Integer weekNumber;

    @Column(name = "deload_week", nullable = false, updatable = false)
    private boolean deloadWeek;

    @Column(name = "day_name", length = 60, updatable = false)
    private String dayName;

    protected WorkoutSession() {
    }

    public WorkoutSession(UUID userId, Instant startedAt) {
        this.userId = userId;
        this.startedAt = startedAt;
    }

    /** Copia as referências e os nomes do planejamento no início da sessão. */
    void linkToPlan(SessionPlanContext context) {
        this.planId = context.planId();
        this.planName = context.planName();
        this.phaseId = context.phaseId();
        this.phaseName = context.phaseName();
        this.workoutDayId = context.dayId();
        this.dayName = context.dayName();
        this.weekId = context.weekId();
        this.weekNumber = context.weekNumber();
        this.deloadWeek = context.deload();
    }

    void finish(Instant at, String notes) {
        this.status = SessionStatus.COMPLETED;
        this.finishedAt = at.isBefore(startedAt) ? startedAt : at;
        if (notes != null) {
            this.notes = notes;
        }
    }

    void updateNotes(String notes) {
        this.notes = notes;
    }

    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getPhaseId() {
        return phaseId;
    }

    public UUID getWeekId() {
        return weekId;
    }

    public UUID getWorkoutDayId() {
        return workoutDayId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getNotes() {
        return notes;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public Integer getWeekNumber() {
        return weekNumber;
    }

    public boolean isDeloadWeek() {
        return deloadWeek;
    }

    public String getDayName() {
        return dayName;
    }
}
