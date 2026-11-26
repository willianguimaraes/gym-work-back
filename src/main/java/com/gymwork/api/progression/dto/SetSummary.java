package com.gymwork.api.progression.dto;

import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;

public record SetSummary(SetType setType, SetStatus status, BigDecimal loadKg, Integer reps, Integer rir) {
}
