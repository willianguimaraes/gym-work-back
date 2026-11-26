package com.gymwork.api.training.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adição ({@code exerciseId} obrigatório) ou edição ({@code exerciseId} ignorado — para trocar o
 * exercício use a substituição) de um exercício planejado. Carga sempre em kg.
 */
public record PlannedExerciseRequest(
        UUID exerciseId,
        @NotNull @Min(1) @Max(20) Integer sets,
        @NotNull @Min(1) @Max(100) Integer repsMin,
        @NotNull @Min(1) @Max(100) Integer repsMax,
        @Min(0) @Max(900) Integer restSeconds,
        @DecimalMin("0") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal suggestedLoadKg,
        @Min(0) @Max(10) Integer targetRir,
        @Size(max = 2000) String notes) {
}
