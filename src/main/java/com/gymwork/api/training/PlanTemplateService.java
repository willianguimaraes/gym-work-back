package com.gymwork.api.training;

import com.gymwork.api.training.dto.PlanTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exporta o planejamento (inteiro ou uma fase) no template {@value PlanTemplate#FORMAT}. */
@Service
public class PlanTemplateService {

    private final TrainingPhaseRepository phaseRepository;
    private final TrainingWeekRepository weekRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;
    private final TrainingLookup lookup;

    public PlanTemplateService(
            TrainingPhaseRepository phaseRepository,
            TrainingWeekRepository weekRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository,
            TrainingLookup lookup) {
        this.phaseRepository = phaseRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    public PlanTemplate plan(UUID userId, UUID planId) {
        TrainingPlan plan = lookup.plan(userId, planId);
        List<PlanTemplate.Phase> phases = phaseRepository.findByPlan(planId, userId).stream()
                .map(phase -> phase(userId, phase))
                .toList();
        return new PlanTemplate(PlanTemplate.FORMAT, plan(plan), phases);
    }

    /** Só uma fase, com os dados do plano a que pertence. */
    @Transactional(readOnly = true)
    public PlanTemplate phase(UUID userId, UUID phaseId) {
        TrainingPhase phase = lookup.phase(userId, phaseId);
        return new PlanTemplate(PlanTemplate.FORMAT, plan(phase.getPlan()), List.of(phase(userId, phase)));
    }

    private static PlanTemplate.Plan plan(TrainingPlan plan) {
        return new PlanTemplate.Plan(plan.getName(), plan.getGoal(), plan.getNotes());
    }

    private PlanTemplate.Phase phase(UUID userId, TrainingPhase phase) {
        List<Integer> deloads = weekRepository.findByPhase(phase.getId(), userId).stream()
                .filter(TrainingWeek::isDeload)
                .map(TrainingWeek::getWeekNumber)
                .sorted()
                .toList();
        List<WorkoutDay> days = dayRepository.findByPhase(phase.getId(), userId);
        Map<UUID, List<PlannedExercise>> exercises = days.isEmpty()
                ? Map.of()
                : plannedExerciseRepository.findByDays(days.stream().map(WorkoutDay::getId).toList(), userId).stream()
                        .collect(Collectors.groupingBy(planned -> planned.getDay().getId()));
        List<PlanTemplate.Day> dayTemplates = days.stream()
                .map(day -> new PlanTemplate.Day(
                        day.getName(),
                        day.getDayOfWeek(),
                        day.getNotes(),
                        exercises.getOrDefault(day.getId(), List.of()).stream()
                                .map(PlanTemplateService::exercise)
                                .toList()))
                .toList();
        return new PlanTemplate.Phase(phase.getName(), phase.getWeeksCount(), deloads, phase.getNotes(), dayTemplates);
    }

    private static PlanTemplate.Exercise exercise(PlannedExercise planned) {
        return new PlanTemplate.Exercise(
                planned.getExercise().getName(),
                planned.getSets(),
                planned.getRepsMin(),
                planned.getRepsMax(),
                planned.getRestSeconds(),
                planned.getTargetRir(),
                planned.getSuggestedLoadKg() == null ? null : planned.getSuggestedLoadKg().stripTrailingZeros(),
                planned.getNotes());
    }
}
