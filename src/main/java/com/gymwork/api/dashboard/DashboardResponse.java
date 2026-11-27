package com.gymwork.api.dashboard;

import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.dashboard.NextWorkout.Reason;
import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import com.gymwork.api.progression.dto.VolumeReport.MuscleVolume;
import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.workout.dto.ObservationResponse;
import com.gymwork.api.workout.dto.SessionSummaryResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tela inicial: o que fazer hoje, como está a frequência e a evolução, em uma resposta só. Campos
 * sem dado vêm nulos ou vazios; nada é estimado.
 */
public record DashboardResponse(
        LocalDate today,
        Plan plan,
        Phase phase,
        Week week,
        Next next,
        SessionSummaryResponse current,
        List<SessionSummaryResponse> recentSessions,
        Body body,
        Frequency frequency,
        Progression progression,
        List<MuscleVolume> muscles,
        List<ObservationResponse> recentObservations) {

    public record Plan(UUID id, String name, PlanGoal goal) {
    }

    public record Phase(UUID id, String name, LocalDate startDate, LocalDate endDate, int weeksCount, int daysCount) {
    }

    public record Week(UUID id, int number, boolean deload, LocalDate startDate) {
    }

    /** Próximo treino; {@code date} só quando o dia tem dia da semana definido. */
    public record Next(
            UUID dayId, String name, DayOfWeek dayOfWeek, int exercisesCount, Instant lastPerformedAt,
            Reason reason, LocalDate date) {
    }

    /**
     * Peso mais recente e o anterior (para a diferença), última avaliação e os pontos dos gráficos de
     * peso e medidas (avaliações mais recentes, até {@value DashboardService#TREND_POINTS}).
     */
    public record Body(
            BigDecimal latestWeightKg,
            LocalDate latestWeightOn,
            BigDecimal previousWeightKg,
            LocalDate previousWeightOn,
            UUID lastAssessmentId,
            LocalDate lastAssessmentOn,
            List<TrendPoint> trend) {
    }

    /** Sessões por semana (segunda a domingo) nas últimas semanas; a última é a semana atual. */
    public record Frequency(int plannedPerWeek, int thisWeek, List<WeekCount> weeks) {
    }

    public record WeekCount(LocalDate weekStart, int sessions, int validSets) {
    }

    /**
     * Análise das últimas semanas: quantos exercícios em cada tendência, alguns de cada grupo e os
     * mais frequentes (para o gráfico de carga).
     */
    public record Progression(
            int weeks,
            Map<Trend, Long> counts,
            List<ExerciseItem> progressing,
            List<ExerciseItem> stable,
            List<ExerciseItem> regressing,
            List<ExerciseItem> mostFrequent) {
    }

    public record ExerciseItem(UUID exerciseId, String name, int executions, String latestSummary, String detail) {
    }
}
