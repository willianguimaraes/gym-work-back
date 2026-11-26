package com.gymwork.api.workout.dto;

import com.gymwork.api.training.TrainingReader.PhaseRef;
import com.gymwork.api.training.TrainingReader.PlanRef;
import com.gymwork.api.training.TrainingReader.WeekRef;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tela "Treinar": a sessão em andamento (se houver) ou os dias da fase do planejamento ativo, com a
 * semana de hoje e a última vez que cada dia foi feito.
 */
public record StartOptionsResponse(
        SessionSummaryResponse current,
        LocalDate today,
        PlanRef plan,
        List<PhaseRef> phases,
        PhaseRef phase,
        WeekRef week,
        List<DayOption> days) {

    public record DayOption(
            UUID id, String name, DayOfWeek dayOfWeek, int exercisesCount, Instant lastPerformedAt) {
    }
}
