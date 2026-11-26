package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.SessionStatus;
import java.time.Instant;
import java.util.UUID;

public record SessionSummaryResponse(
        UUID id,
        SessionStatus status,
        Instant startedAt,
        Instant finishedAt,
        String planName,
        String phaseName,
        Integer weekNumber,
        String dayName,
        int exercisesCount,
        int completedSets) {
}
