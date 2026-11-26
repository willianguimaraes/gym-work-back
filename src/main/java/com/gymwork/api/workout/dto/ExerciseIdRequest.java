package com.gymwork.api.workout.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ExerciseIdRequest(@NotNull UUID exerciseId) {
}
