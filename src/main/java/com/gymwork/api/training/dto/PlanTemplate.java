package com.gymwork.api.training.dto;

import com.gymwork.api.training.PlanGoal;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Template de planejamento {@value #FORMAT}: o mesmo formato serve para exportar o plano, instruir a
 * IA e (Etapa 9) importar o plano devolvido. Os nomes dos campos são estáveis; cargas em kg; os
 * exercícios são referenciados pelo nome do catálogo. Campos sem valor saem como {@code null}.
 */
public record PlanTemplate(String format, Plan plan, List<Phase> phases) {

    public static final String FORMAT = "gymwork.plan/v1";

    public record Plan(String name, PlanGoal goal, String notes) {
    }

    /** {@code deloadWeeks}: números das semanas de deload (1..weeks). */
    public record Phase(String name, int weeks, List<Integer> deloadWeeks, String notes, List<Day> days) {
    }

    public record Day(String name, DayOfWeek dayOfWeek, String notes, List<Exercise> exercises) {
    }

    public record Exercise(
            String exercise,
            int sets,
            int repsMin,
            int repsMax,
            Integer restSeconds,
            Integer targetRir,
            BigDecimal suggestedLoadKg,
            String notes) {
    }
}
