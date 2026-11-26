package com.gymwork.api.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Avaliação física. Tudo é opcional, menos a data: uma avaliação só com o peso é o "registro
 * rápido de peso" (A6). Medidas em centímetros, peso em kg.
 */
public record AssessmentRequest(
        @NotNull LocalDate assessedOn,
        @DecimalMin("20") @DecimalMax("400") @Digits(integer = 3, fraction = 2) BigDecimal weightKg,
        @DecimalMin("50") @DecimalMax("260") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
        @DecimalMin("1") @DecimalMax("75") @Digits(integer = 2, fraction = 1) BigDecimal bodyFatPct,
        @Size(max = 2000) String notes,
        @Size(max = 12) List<@Valid MeasurementValue> measurements) {
}
