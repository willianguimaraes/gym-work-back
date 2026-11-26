package com.gymwork.api.workout;

import java.math.BigDecimal;
import java.util.UUID;

/** Dados de uma série já validados pelo serviço. */
record SetData(
        SetType setType,
        UUID parentSetId,
        SetStatus status,
        BigDecimal loadKg,
        Integer reps,
        Integer rir,
        Integer restSeconds,
        String notes) {
}
