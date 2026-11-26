package com.gymwork.api.training.dto;

import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.training.PlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Item da lista de planejamentos. {@code totalWeeks} é a soma das semanas das fases (A2). */
public record PlanSummaryResponse(
        UUID id,
        String name,
        PlanGoal goal,
        PlanStatus status,
        LocalDate startDate,
        LocalDate expectedEndDate,
        int phasesCount,
        int totalWeeks,
        Instant updatedAt) {
}
