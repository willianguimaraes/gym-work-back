package com.gymwork.api.training.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PhaseSummaryResponse(
        UUID id,
        String name,
        int position,
        LocalDate startDate,
        LocalDate endDate,
        int weeksCount,
        int daysCount) {
}
