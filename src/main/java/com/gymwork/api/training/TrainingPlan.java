package com.gymwork.api.training;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Planejamento: o ciclo maior, com várias fases em ordem (A2). */
@Entity
@Table(name = "training_plans")
public class TrainingPlan extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expected_end_date")
    private LocalDate expectedEndDate;

    @Column(length = 2000)
    private String notes;

    protected TrainingPlan() {
    }

    public TrainingPlan(UUID userId) {
        this.userId = userId;
    }

    public void update(String name, PlanGoal goal, LocalDate startDate, LocalDate expectedEndDate, String notes) {
        this.name = name;
        this.goal = goal;
        this.startDate = startDate;
        this.expectedEndDate = expectedEndDate;
        this.notes = notes;
    }

    void changeStatus(PlanStatus status) {
        this.status = status;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public PlanGoal getGoal() {
        return goal;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getExpectedEndDate() {
        return expectedEndDate;
    }

    public String getNotes() {
        return notes;
    }
}
