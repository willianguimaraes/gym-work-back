package com.gymwork.api.training.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.training.PlannedExercise;
import java.math.BigDecimal;
import java.util.UUID;

public record PlannedExerciseResponse(
        UUID id,
        int position,
        ExerciseRef exercise,
        int sets,
        int repsMin,
        int repsMax,
        Integer restSeconds,
        BigDecimal suggestedLoadKg,
        Integer targetRir,
        String notes) {

    /** Resumo do exercício do catálogo usado no plano. */
    public record ExerciseRef(UUID id, String name, String primaryMuscleGroup, Equipment equipment, boolean active) {

        static ExerciseRef from(Exercise exercise) {
            return new ExerciseRef(
                    exercise.getId(), exercise.getName(), exercise.getPrimaryMuscleGroup(),
                    exercise.getEquipment(), exercise.isActive());
        }
    }

    public static PlannedExerciseResponse from(PlannedExercise planned) {
        return new PlannedExerciseResponse(
                planned.getId(),
                planned.getPosition(),
                ExerciseRef.from(planned.getExercise()),
                planned.getSets(),
                planned.getRepsMin(),
                planned.getRepsMax(),
                planned.getRestSeconds(),
                planned.getSuggestedLoadKg(),
                planned.getTargetRir(),
                planned.getNotes());
    }
}
