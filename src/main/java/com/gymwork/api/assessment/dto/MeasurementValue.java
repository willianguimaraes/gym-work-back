package com.gymwork.api.assessment.dto;

import com.gymwork.api.assessment.MeasurementType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Medida em centímetros (entrada e saída). */
public record MeasurementValue(
        @NotNull MeasurementType type,
        @NotNull @DecimalMin("5") @DecimalMax("300") @Digits(integer = 3, fraction = 1) BigDecimal valueCm) {
}
