package com.gymwork.api.assessment.dto;

import com.gymwork.api.assessment.MeasurementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Ponto dos gráficos de peso, gordura e medidas (só os valores informados). */
public record TrendPoint(
        UUID assessmentId,
        LocalDate assessedOn,
        BigDecimal weightKg,
        BigDecimal bodyFatPct,
        Map<MeasurementType, BigDecimal> measurements) {
}
