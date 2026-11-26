package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Última vez que o exercício foi feito (só séries válidas concluídas), para sugerir carga e reps. */
public record LastExecutionResponse(UUID sessionId, Instant performedAt, List<LastSet> sets) {

    public record LastSet(SetType setType, BigDecimal loadKg, Integer reps, Integer rir) {
    }
}
