package com.gymwork.api.report;

import com.gymwork.api.workout.BodyRegion;
import com.gymwork.api.workout.BodySide;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dados de um relatório (sessão, semana ou fase), montados só a partir do que foi registrado. É a
 * fonte única do texto, do CSV, do PDF, da análise e do pacote para IA: nada é inventado, campo
 * sem valor fica {@code null}.
 *
 * @param phaseName {@code null} em treino avulso (sem planejamento)
 */
public record Report(
        ReportScope scope,
        String planName,
        String phaseName,
        LocalDate from,
        LocalDate to,
        List<Week> weeks) {

    /** Sessões de todos os dias, em ordem. */
    public List<Session> sessions() {
        return weeks.stream().flatMap(week -> week.days().stream())
                .map(Day::session)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Semana do plano. {@code number} nulo agrupa sessões da fase sem semana definida (fase sem data
     * de início). {@code bodyWeightKg}: avaliação mais recente dentro da semana (A6).
     */
    public record Week(
            Integer number,
            boolean deload,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal bodyWeightKg,
            List<Day> days) {
    }

    /** Dia de treino. {@code session} nulo: dia planejado que não foi feito na semana. */
    public record Day(DayOfWeek weekday, String name, UUID workoutDayId, Session session) {
    }

    public record Session(
            UUID id,
            LocalDate date,
            Instant startedAt,
            boolean inProgress,
            String notes,
            List<Exercise> exercises,
            List<Observation> observations) {
    }

    /**
     * Exercício feito na sessão. {@code replaces}: nome do exercício planejado que ele substituiu.
     * {@code sets}: séries na ordem, com drop set e rest-pause dentro da série principal.
     */
    public record Exercise(
            UUID sessionExerciseId,
            UUID exerciseId,
            String name,
            String muscleGroup,
            String replaces,
            SessionExerciseStatus status,
            Integer plannedSets,
            Integer repsMin,
            Integer repsMax,
            Integer targetRir,
            String notes,
            List<Set> sets) {
    }

    public record Set(
            int number,
            SetType type,
            SetStatus status,
            BigDecimal loadKg,
            Integer reps,
            Integer rir,
            String notes,
            List<Set> extensions) {
    }

    public record Observation(
            ObservationCategory category,
            BodyRegion bodyRegion,
            BodySide bodySide,
            Integer intensity,
            boolean interrupted,
            String exerciseName,
            String text,
            Instant observedAt) {
    }
}
