package com.gymwork.api.training.dto;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/** Dia de treino com os exercícios em ordem. {@code planId}/{@code phaseName} servem à navegação. */
public record DayResponse(
        UUID id,
        UUID planId,
        UUID phaseId,
        String phaseName,
        String name,
        DayOfWeek dayOfWeek,
        int position,
        String notes,
        List<PlannedExerciseResponse> exercises) {
}
