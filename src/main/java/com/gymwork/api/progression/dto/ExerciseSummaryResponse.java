package com.gymwork.api.progression.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.workout.dto.ObservationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resumo da evolução de um exercício: última execução, melhor desempenho, análise de progressão,
 * regra e observações ligadas a ele.
 */
public record ExerciseSummaryResponse(
        UUID exerciseId,
        String exerciseName,
        Equipment equipment,
        int executions,
        Instant lastPerformedAt,
        String lastSummary,
        BigDecimal lastTopLoadKg,
        List<Integer> lastReps,
        BestSet bestSet,
        BigDecimal bestEstimatedOneRepMaxKg,
        Instant bestEstimatedOneRepMaxAt,
        AnalysisResponse analysis,
        RuleResponse rule,
        List<ObservationResponse> observations) {

    /** Série mais pesada já registrada (maior carga; no empate, mais repetições). */
    public record BestSet(BigDecimal loadKg, Integer reps, Instant performedAt) {
    }
}
