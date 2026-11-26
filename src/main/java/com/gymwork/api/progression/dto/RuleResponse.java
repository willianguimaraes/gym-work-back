package com.gymwork.api.progression.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Regra efetiva do exercício. {@code custom = false}: regra padrão (dupla progressão). */
public record RuleResponse(
        UUID exerciseId,
        boolean custom,
        Integer minRir,
        int sessionsAtTop,
        BigDecimal loadIncrementKg,
        BigDecimal effectiveIncrementKg,
        boolean enabled) {
}
