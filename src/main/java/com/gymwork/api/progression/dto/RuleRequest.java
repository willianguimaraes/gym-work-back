package com.gymwork.api.progression.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Ajuste da regra de progressão de um exercício.
 *
 * @param minRir RIR mínimo em todas as séries para sugerir aumento (nulo = RIR alvo do plano)
 * @param sessionsAtTop sessões seguidas no teto da faixa antes de sugerir aumento (1 a 5)
 * @param loadIncrementKg incremento sugerido em kg (nulo = 2,5 kg)
 */
public record RuleRequest(
        @Min(0) @Max(10) Integer minRir,
        @NotNull @Min(1) @Max(5) Integer sessionsAtTop,
        @DecimalMin(value = "0.25") @DecimalMax("50") @Digits(integer = 3, fraction = 2) BigDecimal loadIncrementKg,
        @NotNull Boolean enabled) {
}
