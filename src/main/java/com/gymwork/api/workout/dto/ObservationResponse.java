package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.BodyRegion;
import com.gymwork.api.workout.BodySide;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.ObservationMoment;
import com.gymwork.api.workout.WorkoutObservation;
import java.time.Instant;
import java.util.UUID;

public record ObservationResponse(
        UUID id,
        ObservationCategory category,
        UUID sessionId,
        UUID sessionExerciseId,
        UUID exerciseId,
        BodyRegion bodyRegion,
        BodySide bodySide,
        Integer intensity,
        ObservationMoment moment,
        boolean interrupted,
        String text,
        Instant observedAt) {

    public static ObservationResponse from(WorkoutObservation observation) {
        return new ObservationResponse(
                observation.getId(),
                observation.getCategory(),
                observation.getSessionId(),
                observation.getSessionExerciseId(),
                observation.getExerciseId(),
                observation.getBodyRegion(),
                observation.getBodySide(),
                observation.getIntensity(),
                observation.getMoment(),
                observation.isInterrupted(),
                observation.getText(),
                observation.getObservedAt());
    }
}
