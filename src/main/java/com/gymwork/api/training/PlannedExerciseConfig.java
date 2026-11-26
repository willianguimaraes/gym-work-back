package com.gymwork.api.training;

import java.math.BigDecimal;

/** Configuração planejada de um exercício (copiada na duplicação e mantida na substituição). */
public record PlannedExerciseConfig(
        int sets,
        int repsMin,
        int repsMax,
        Integer restSeconds,
        BigDecimal suggestedLoadKg,
        Integer targetRir,
        String notes) {
}
