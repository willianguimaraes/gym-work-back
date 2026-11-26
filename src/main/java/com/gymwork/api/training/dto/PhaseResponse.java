package com.gymwork.api.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalhe da fase: semanas e dias de treino com os exercícios planejados. */
public record PhaseResponse(
        UUID id,
        UUID planId,
        String planName,
        String name,
        int position,
        LocalDate startDate,
        LocalDate endDate,
        int weeksCount,
        String notes,
        List<WeekResponse> weeks,
        List<DayResponse> days) {
}
