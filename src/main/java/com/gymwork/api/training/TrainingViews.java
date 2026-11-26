package com.gymwork.api.training;

import com.gymwork.api.training.dto.DayResponse;
import com.gymwork.api.training.dto.PhaseResponse;
import com.gymwork.api.training.dto.PhaseSummaryResponse;
import com.gymwork.api.training.dto.PlanResponse;
import com.gymwork.api.training.dto.PlannedExerciseResponse;
import com.gymwork.api.training.dto.WeekResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Monta as respostas do planejamento com poucas consultas (sem N+1). */
@Component
class TrainingViews {

    private final TrainingPhaseRepository phaseRepository;
    private final TrainingWeekRepository weekRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;

    TrainingViews(
            TrainingPhaseRepository phaseRepository,
            TrainingWeekRepository weekRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository) {
        this.phaseRepository = phaseRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
    }

    PlanResponse plan(TrainingPlan plan) {
        UUID userId = plan.getUserId();
        Map<UUID, Long> daysByPhase = new HashMap<>();
        for (Object[] row : dayRepository.countByPhaseOfPlan(plan.getId(), userId)) {
            daysByPhase.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        List<PhaseSummaryResponse> phases = phaseRepository.findByPlan(plan.getId(), userId).stream()
                .map(phase -> new PhaseSummaryResponse(
                        phase.getId(),
                        phase.getName(),
                        phase.getPosition(),
                        phase.getStartDate(),
                        phase.getEndDate(),
                        phase.getWeeksCount(),
                        daysByPhase.getOrDefault(phase.getId(), 0L).intValue()))
                .toList();
        int totalWeeks = phases.stream().mapToInt(PhaseSummaryResponse::weeksCount).sum();
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getGoal(),
                plan.getStatus(),
                plan.getStartDate(),
                plan.getExpectedEndDate(),
                plan.getNotes(),
                totalWeeks,
                phases,
                plan.getUpdatedAt());
    }

    PhaseResponse phase(TrainingPhase phase) {
        UUID userId = phase.getUserId();
        List<WeekResponse> weeks = weekRepository.findByPhase(phase.getId(), userId).stream()
                .map(WeekResponse::from)
                .toList();
        List<WorkoutDay> days = dayRepository.findByPhase(phase.getId(), userId);
        Map<UUID, List<PlannedExerciseResponse>> exercisesByDay = exercisesByDay(days, userId);
        List<DayResponse> dayResponses = days.stream()
                .map(day -> day(day, phase, exercisesByDay.getOrDefault(day.getId(), List.of())))
                .toList();
        return new PhaseResponse(
                phase.getId(),
                phase.getPlan().getId(),
                phase.getPlan().getName(),
                phase.getName(),
                phase.getPosition(),
                phase.getStartDate(),
                phase.getEndDate(),
                phase.getWeeksCount(),
                phase.getNotes(),
                weeks,
                dayResponses);
    }

    DayResponse day(WorkoutDay day) {
        List<PlannedExerciseResponse> exercises = exercisesByDay(List.of(day), day.getUserId())
                .getOrDefault(day.getId(), List.of());
        return day(day, day.getPhase(), exercises);
    }

    private Map<UUID, List<PlannedExerciseResponse>> exercisesByDay(List<WorkoutDay> days, UUID userId) {
        if (days.isEmpty()) {
            return Map.of();
        }
        List<UUID> dayIds = days.stream().map(WorkoutDay::getId).toList();
        return plannedExerciseRepository.findByDays(dayIds, userId).stream()
                .collect(Collectors.groupingBy(
                        planned -> planned.getDay().getId(),
                        Collectors.mapping(PlannedExerciseResponse::from, Collectors.toList())));
    }

    private static DayResponse day(WorkoutDay day, TrainingPhase phase, List<PlannedExerciseResponse> exercises) {
        return new DayResponse(
                day.getId(),
                phase.getPlan().getId(),
                phase.getId(),
                phase.getName(),
                day.getName(),
                day.getDayOfWeek(),
                day.getPosition(),
                day.getNotes(),
                exercises);
    }
}
