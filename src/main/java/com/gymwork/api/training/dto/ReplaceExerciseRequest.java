package com.gymwork.api.training.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReplaceExerciseRequest(@NotNull UUID exerciseId) {
}
