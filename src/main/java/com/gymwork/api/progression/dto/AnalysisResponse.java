package com.gymwork.api.progression.dto;

import com.gymwork.api.progression.ProgressionAnalyzer.Suggestion;
import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import java.math.BigDecimal;

/** Resultado da análise de progressão. As sugestões são só informativas. */
public record AnalysisResponse(
        Trend trend,
        String trendDetail,
        Suggestion suggestion,
        String message,
        BigDecimal suggestedLoadKg,
        String latestSummary,
        String previousSummary,
        boolean painReported) {
}
