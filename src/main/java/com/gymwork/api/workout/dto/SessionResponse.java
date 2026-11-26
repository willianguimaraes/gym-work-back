package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sessão completa. {@code plannedSets} e {@code completedSets} formam o progresso ("12 de 20
 * séries"); aquecimento, ativação, drop set e rest-pause não contam como séries.
 */
public record SessionResponse(
        UUID id,
        SessionStatus status,
        Instant startedAt,
        Instant finishedAt,
        String notes,
        UUID planId,
        UUID phaseId,
        UUID weekId,
        UUID workoutDayId,
        String planName,
        String phaseName,
        Integer weekNumber,
        boolean deloadWeek,
        String dayName,
        int plannedSets,
        int completedSets,
        List<SessionExerciseResponse> exercises,
        List<ObservationResponse> observations) {
}
