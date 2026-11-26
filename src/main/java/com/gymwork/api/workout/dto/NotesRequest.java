package com.gymwork.api.workout.dto;

import jakarta.validation.constraints.Size;

public record NotesRequest(@Size(max = 2000) String notes) {
}
