package com.gymwork.api.training.dto;

import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.training.PlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Criação ou edição de um planejamento. {@code status} é opcional: na criação o padrão é DRAFT;
 * ACTIVE desativa (marca como COMPLETED) o planejamento ativo anterior.
 */
public record PlanRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull PlanGoal goal,
        LocalDate startDate,
        LocalDate expectedEndDate,
        PlanStatus status,
        @Size(max = 2000) String notes) {
}
