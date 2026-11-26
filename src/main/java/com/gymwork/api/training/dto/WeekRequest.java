package com.gymwork.api.training.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WeekRequest(@NotNull Boolean deload, @Size(max = 2000) String notes) {
}
