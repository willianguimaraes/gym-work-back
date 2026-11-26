package com.gymwork.api.workout;

import java.time.Instant;
import java.util.UUID;

/** Dados de uma observação já validados pelo serviço. */
record ObservationData(
        UUID sessionId,
        UUID sessionExerciseId,
        UUID exerciseId,
        ObservationCategory category,
        BodyRegion bodyRegion,
        BodySide bodySide,
        Integer intensity,
        ObservationMoment moment,
        boolean interrupted,
        String text,
        Instant observedAt) {
}
