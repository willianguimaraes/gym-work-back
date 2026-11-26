package com.gymwork.api.report.dto;

import com.gymwork.api.progression.ScopeAnalysisService.ExerciseChange;
import com.gymwork.api.report.ReportScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumo estruturado do período, base para avaliação por treinador ou IA. Só usa dados
 * registrados; o que não existe fica vazio.
 *
 * @param inconclusive exercícios com execução incompleta ou poucos dados para comparar
 * @param loadChanges "Supino: 45 kg → 47,5 kg"
 * @param repChanges "Supino (45 kg): 8/7/6 → 8/8/7"
 */
public record AnalysisReport(
        ReportScope scope,
        String title,
        String planName,
        String phaseName,
        LocalDate from,
        LocalDate to,
        Frequency frequency,
        List<ExerciseChange> progressed,
        List<ExerciseChange> maintained,
        List<ExerciseChange> regressed,
        List<ExerciseChange> inconclusive,
        List<String> loadChanges,
        List<String> repChanges,
        List<Missed> notPerformed,
        List<String> observations,
        List<String> discomforts,
        List<MuscleVolume> volume,
        int validSets,
        BigDecimal volumeKg) {

    /**
     * @param plannedSessions dias planejados × semanas consideradas (nulo sem semana definida)
     * @param sessionsPerWeek sessões finalizadas por semana considerada
     */
    public record Frequency(
            int completedSessions,
            int inProgressSessions,
            Integer plannedSessions,
            int missedDays,
            int weeks,
            BigDecimal sessionsPerWeek) {
    }

    /**
     * @param when "Semana 2 · SEG — UPPER"
     * @param exercise nulo quando o dia inteiro não foi feito
     */
    public record Missed(String when, String exercise, String detail) {
    }

    public record MuscleVolume(String muscleGroup, String name, int sets, int secondarySets) {
    }
}
