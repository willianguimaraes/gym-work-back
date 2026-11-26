package com.gymwork.api.exercise.dto;

import jakarta.validation.constraints.NotNull;

public record ActiveRequest(@NotNull Boolean active) {
}
