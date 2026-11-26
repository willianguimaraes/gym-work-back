package com.gymwork.api.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Criação ou edição de uma fase. Mudar {@code weeksCount} cria ou remove semanas no fim; mudar
 * {@code startDate} recalcula a data de início de cada semana.
 */
public record PhaseRequest(
        @NotBlank @Size(max = 120) String name,
        LocalDate startDate,
        @NotNull @Min(1) @Max(52) Integer weeksCount,
        @Size(max = 2000) String notes) {
}
