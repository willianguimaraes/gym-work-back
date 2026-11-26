package com.gymwork.api.progression;

import java.math.BigDecimal;

/**
 * Parâmetros da regra de progressão.
 *
 * @param minRir RIR mínimo em todas as séries para sugerir aumento; nulo = RIR alvo do plano
 * @param sessionsAtTop sessões seguidas no teto da faixa antes de sugerir aumento
 * @param loadIncrementKg incremento sugerido; nulo = {@link #DEFAULT_INCREMENT}
 * @param enabled sem regra ativa, a análise mostra a tendência mas não faz sugestão
 */
public record RuleSettings(Integer minRir, int sessionsAtTop, BigDecimal loadIncrementKg, boolean enabled) {

    public static final BigDecimal DEFAULT_INCREMENT = new BigDecimal("2.5");
    public static final RuleSettings DEFAULT = new RuleSettings(null, 1, null, true);

    public BigDecimal increment() {
        return loadIncrementKg == null ? DEFAULT_INCREMENT : loadIncrementKg;
    }
}
