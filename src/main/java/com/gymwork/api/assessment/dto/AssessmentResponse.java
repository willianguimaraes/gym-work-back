package com.gymwork.api.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        LocalDate assessedOn,
        BigDecimal weightKg,
        BigDecimal heightCm,
        BigDecimal bodyFatPct,
        String notes,
        List<MeasurementValue> measurements,
        List<PhotoResponse> photos) {
}
