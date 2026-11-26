package com.gymwork.api.progression;

import com.gymwork.api.progression.ProgressionAnalyzer.Analysis;
import com.gymwork.api.progression.dto.AnalysisResponse;
import com.gymwork.api.progression.dto.ExerciseHistoryItem;
import com.gymwork.api.progression.dto.SetSummary;

/** Conversões dos registros de execução para as respostas da API. */
final class Views {

    private Views() {
    }

    static ExerciseHistoryItem historyItem(ExecutionRecord record) {
        return new ExerciseHistoryItem(
                record.sessionId(),
                record.performedAt(),
                record.dayName(),
                record.phaseName(),
                record.weekNumber(),
                record.deload(),
                record.status(),
                record.isComplete(),
                record.plannedSets(),
                record.repsMin(),
                record.repsMax(),
                record.summary(),
                record.topLoad(),
                record.validSets().size(),
                record.totalReps(),
                record.volumeKg(),
                record.painReported(),
                record.sets().stream()
                        .map(set -> new SetSummary(set.type(), set.status(), set.loadKg(), set.reps(), set.rir()))
                        .toList());
    }

    static AnalysisResponse analysis(Analysis analysis) {
        return new AnalysisResponse(
                analysis.trend(),
                analysis.trendDetail(),
                analysis.suggestion(),
                analysis.message(),
                analysis.suggestedLoadKg(),
                analysis.latest() == null ? null : analysis.latest().summary(),
                analysis.previous() == null ? null : analysis.previous().summary(),
                analysis.painReported());
    }
}
