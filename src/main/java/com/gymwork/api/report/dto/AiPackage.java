package com.gymwork.api.report.dto;

import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.training.dto.PlanTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Pacote de acompanhamento de uma fase para uma IA (ou treinador) avaliar a evolução e devolver o
 * próximo plano no template {@value PlanTemplate#FORMAT}. Unidades sempre em kg e cm.
 */
public record AiPackage(
        String format,
        Instant generatedAt,
        String instructions,
        Context context,
        PlanTemplate currentPlan,
        List<TemplateField> templateFields,
        String executionText,
        AnalysisReport analysis,
        List<TrendPoint> assessments,
        List<CatalogItem> catalog) {

    public static final String FORMAT = "gymwork.ai-package/v1";

    public record Context(
            String planName,
            PlanGoal goal,
            String phaseName,
            int phaseWeeks,
            LocalDate phaseStart,
            LocalDate phaseEnd,
            LocalDate periodFrom,
            LocalDate periodTo,
            String weightUnit,
            String lengthUnit) {
    }

    public record TemplateField(String field, String description) {
    }

    public record CatalogItem(String name, String muscleGroup, String equipment) {
    }
}
