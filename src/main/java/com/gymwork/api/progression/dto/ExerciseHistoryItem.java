package com.gymwork.api.progression.dto;

import com.gymwork.api.workout.SessionExerciseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Uma execução do exercício: contexto, planejado, séries e resumo ("45 kg — 8/7/6/5"). */
public record ExerciseHistoryItem(
        UUID sessionId,
        Instant performedAt,
        String dayName,
        String phaseName,
        Integer weekNumber,
        boolean deload,
        SessionExerciseStatus status,
        boolean complete,
        Integer plannedSets,
        Integer repsMin,
        Integer repsMax,
        String summary,
        BigDecimal topLoadKg,
        int validSets,
        int totalReps,
        BigDecimal volumeKg,
        boolean painReported,
        List<SetSummary> sets) {
}
