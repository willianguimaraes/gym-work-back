package com.gymwork.api.workout.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.workout.SessionExerciseStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Exercício da sessão com a configuração planejada (copiada), as séries e a última execução. */
public record SessionExerciseResponse(
        UUID id,
        int position,
        UUID exerciseId,
        String exerciseName,
        Equipment equipment,
        String muscleGroup,
        UUID plannedExerciseId,
        SessionExerciseStatus status,
        UUID substitutedById,
        String notes,
        Integer plannedSets,
        Integer repsMin,
        Integer repsMax,
        Integer restSeconds,
        Integer targetRir,
        BigDecimal referenceLoadKg,
        List<SetResponse> sets,
        LastExecutionResponse lastExecution) {
}
