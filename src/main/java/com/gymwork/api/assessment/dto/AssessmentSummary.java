package com.gymwork.api.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssessmentSummary(
        UUID id,
        LocalDate assessedOn,
        BigDecimal weightKg,
        BigDecimal bodyFatPct,
        int measurementsCount,
        int photosCount) {
}
