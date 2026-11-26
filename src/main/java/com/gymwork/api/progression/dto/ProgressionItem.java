package com.gymwork.api.progression.dto;

import com.gymwork.api.exercise.Equipment;
import java.time.Instant;
import java.util.UUID;

public record ProgressionItem(
        UUID exerciseId,
        String exerciseName,
        Equipment equipment,
        String muscleGroup,
        int executions,
        Instant lastPerformedAt,
        AnalysisResponse analysis) {
}
