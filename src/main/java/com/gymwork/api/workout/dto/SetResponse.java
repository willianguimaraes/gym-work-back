package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.ExerciseSet;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SetResponse(
        UUID id,
        UUID sessionExerciseId,
        UUID clientId,
        int setNumber,
        SetType setType,
        UUID parentSetId,
        SetStatus status,
        BigDecimal loadKg,
        Integer reps,
        Integer rir,
        Integer restSeconds,
        String notes,
        Instant performedAt) {

    public static SetResponse from(ExerciseSet set) {
        return new SetResponse(
                set.getId(),
                set.getSessionExercise().getId(),
                set.getClientId(),
                set.getSetNumber(),
                set.getSetType(),
                set.getParentSetId(),
                set.getStatus(),
                set.getLoadKg(),
                set.getReps(),
                set.getRir(),
                set.getRestSeconds(),
                set.getNotes(),
                set.getPerformedAt());
    }
}
