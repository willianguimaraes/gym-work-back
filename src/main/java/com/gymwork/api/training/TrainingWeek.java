package com.gymwork.api.training;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Semana da fase (1..N), gerada a partir da duração. Guarda o contexto: deload e observações. */
@Entity
@Table(name = "training_weeks")
public class TrainingWeek extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false, updatable = false)
    private TrainingPhase phase;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "week_number", nullable = false, updatable = false)
    private int weekNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "is_deload", nullable = false)
    private boolean deload;

    @Column(length = 2000)
    private String notes;

    protected TrainingWeek() {
    }

    public TrainingWeek(TrainingPhase phase, int weekNumber) {
        this.phase = phase;
        this.userId = phase.getUserId();
        this.weekNumber = weekNumber;
        this.startDate = phase.weekStart(weekNumber);
    }

    public void update(boolean deload, String notes) {
        this.deload = deload;
        this.notes = notes;
    }

    void refreshStartDate() {
        this.startDate = phase.weekStart(weekNumber);
    }

    public TrainingPhase getPhase() {
        return phase;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public boolean isDeload() {
        return deload;
    }

    public String getNotes() {
        return notes;
    }
}
