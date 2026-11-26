package com.gymwork.api.progression.dto;

import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exercícios em progresso, estáveis, em queda, incompletos ou com poucos dados, no período ou fase. */
public record ProgressionReport(
        UUID phaseId,
        String phaseName,
        LocalDate from,
        LocalDate to,
        Map<Trend, Long> counts,
        List<ProgressionItem> items) {
}
