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

/** Fase do planejamento. Tem a duração em semanas e o modelo semanal de dias de treino (A1). */
@Entity
@Table(name = "training_phases")
public class TrainingPhase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private TrainingPlan plan;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "weeks_count", nullable = false)
    private int weeksCount;

    @Column(length = 2000)
    private String notes;

    protected TrainingPhase() {
    }

    public TrainingPhase(TrainingPlan plan, int position) {
        this.plan = plan;
        this.userId = plan.getUserId();
        this.position = position;
    }

    public void update(String name, LocalDate startDate, int weeksCount, String notes) {
        this.name = name;
        this.startDate = startDate;
        this.weeksCount = weeksCount;
        this.notes = notes;
    }

    void moveTo(int position) {
        this.position = position;
    }

    /** Último dia da fase, se a data de início for conhecida. */
    public LocalDate getEndDate() {
        return startDate == null ? null : startDate.plusWeeks(weeksCount).minusDays(1);
    }

    /** Data de início da semana N (1..weeksCount), se a fase tiver data de início. */
    LocalDate weekStart(int weekNumber) {
        return startDate == null ? null : startDate.plusWeeks(weekNumber - 1L);
    }

    public TrainingPlan getPlan() {
        return plan;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public int getWeeksCount() {
        return weeksCount;
    }

    public String getNotes() {
        return notes;
    }
}
