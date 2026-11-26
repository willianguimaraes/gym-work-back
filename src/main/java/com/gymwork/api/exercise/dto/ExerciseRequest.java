package com.gymwork.api.exercise.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.ExerciseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Criação ou edição de um exercício personalizado. Grupos musculares pelo código (ex.: CHEST). */
public record ExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String primaryMuscleGroup,
        @Size(max = 10) List<String> secondaryMuscleGroups,
        @NotNull ExerciseType type,
        @NotNull Equipment equipment,
        @Size(max = 1000) String description,
        @Size(max = 4000) String instructions) {
}
