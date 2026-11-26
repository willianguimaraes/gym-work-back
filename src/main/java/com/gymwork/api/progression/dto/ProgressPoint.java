package com.gymwork.api.progression.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ponto do gráfico de evolução (por sessão, semana ou fase). {@code repsAtTopLoad} é a média de
 * repetições nas séries feitas com a maior carga do período.
 */
public record ProgressPoint(
        String label,
        LocalDate date,
        int executions,
        BigDecimal topLoadKg,
        BigDecimal repsAtTopLoad,
        int validSets,
        int totalReps,
        BigDecimal volumeKg,
        BigDecimal bestEstimatedOneRepMaxKg) {
}
