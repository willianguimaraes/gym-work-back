package com.gymwork.api.training.dto;

import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.training.PlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalhe do planejamento, com as fases em ordem. */
public record PlanResponse(
        UUID id,
        String name,
        PlanGoal goal,
        PlanStatus status,
        LocalDate startDate,
        LocalDate expectedEndDate,
        String notes,
        int totalWeeks,
        List<PhaseSummaryResponse> phases,
        Instant updatedAt) {
}
