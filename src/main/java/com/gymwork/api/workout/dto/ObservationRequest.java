package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.BodyRegion;
import com.gymwork.api.workout.BodySide;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.ObservationMoment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Observação. Região e lado do corpo só valem para dor e desconforto. Com {@code sessionExerciseId},
 * a sessão e o exercício são deduzidos dele.
 */
public record ObservationRequest(
        @NotNull ObservationCategory category,
        UUID sessionId,
        UUID sessionExerciseId,
        UUID exerciseId,
        BodyRegion bodyRegion,
        BodySide bodySide,
        @Min(0) @Max(10) Integer intensity,
        ObservationMoment moment,
        Boolean interrupted,
        @Size(max = 2000) String text,
        Instant observedAt) {
}
