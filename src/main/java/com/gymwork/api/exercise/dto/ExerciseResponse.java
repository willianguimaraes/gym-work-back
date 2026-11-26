package com.gymwork.api.exercise.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseType;
import java.util.List;
import java.util.UUID;

/** @param custom {@code true} = exercício personalizado do usuário (editável) */
public record ExerciseResponse(
        UUID id,
        String name,
        String primaryMuscleGroup,
        List<String> secondaryMuscleGroups,
        ExerciseType type,
        Equipment equipment,
        String description,
        String instructions,
        boolean active,
        boolean custom) {

    public static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getPrimaryMuscleGroup(),
                exercise.getSecondaryMuscleGroups().stream().sorted().toList(),
                exercise.getType(),
                exercise.getEquipment(),
                exercise.getDescription(),
                exercise.getInstructions(),
                exercise.isActive(),
                !exercise.isGlobal());
    }
}
