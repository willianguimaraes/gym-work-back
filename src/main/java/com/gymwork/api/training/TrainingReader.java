package com.gymwork.api.training;

import com.gymwork.api.exercise.Exercise;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do planejamento para outros módulos (execução do treino). Sempre filtrada pelo usuário;
 * devolve dados prontos, sem expor as entidades do planejamento para alteração.
 */
@Service
public class TrainingReader {

    private final TrainingPlanRepository planRepository;
    private final TrainingPhaseRepository phaseRepository;
    private final TrainingWeekRepository weekRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;
    private final TrainingLookup lookup;

    public TrainingReader(
            TrainingPlanRepository planRepository,
            TrainingPhaseRepository phaseRepository,
            TrainingWeekRepository weekRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository,
            TrainingLookup lookup) {
        this.planRepository = planRepository;
        this.phaseRepository = phaseRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
        this.lookup = lookup;
    }

    public record PlanRef(UUID id, String name) {
    }

    public record PhaseRef(UUID id, String name, int position, LocalDate startDate, LocalDate endDate, int weeksCount) {

        boolean contains(LocalDate date) {
            return startDate != null && !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    public record WeekRef(UUID id, UUID phaseId, int weekNumber, LocalDate startDate, boolean deload) {
    }

    public record DayRef(UUID id, String name, DayOfWeek dayOfWeek, int position, int exercisesCount) {
    }

    /** Exercício planejado pronto para virar exercício da sessão. */
    public record PlannedItem(
            UUID plannedExerciseId,
            Exercise exercise,
            int position,
            PlannedExerciseConfig config) {
    }

    /** Dia de treino com o contexto (plano, fase) e os exercícios em ordem. */
    public record DayPlan(
            UUID planId,
            String planName,
            UUID phaseId,
            String phaseName,
            UUID dayId,
            String dayName,
            List<PlannedItem> exercises) {
    }

    @Transactional(readOnly = true)
    public Optional<PlanRef> activePlan(UUID userId) {
        return planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                .map(plan -> new PlanRef(plan.getId(), plan.getName()));
    }

    @Transactional(readOnly = true)
    public List<PhaseRef> phases(UUID userId, UUID planId) {
        return phaseRepository.findByPlan(planId, userId).stream().map(TrainingReader::phaseRef).toList();
    }

    /**
     * Fase "de hoje": a que contém a data; senão a última que já começou; senão a primeira.
     * Vazio se o plano não tiver fases.
     */
    @Transactional(readOnly = true)
    public Optional<PhaseRef> phaseOn(UUID userId, UUID planId, LocalDate date) {
        List<PhaseRef> phases = phases(userId, planId);
        return phases.stream().filter(phase -> phase.contains(date)).findFirst()
                .or(() -> phases.stream()
                        .filter(phase -> phase.startDate() != null && !phase.startDate().isAfter(date))
                        .reduce((first, second) -> second))
                .or(() -> phases.stream().findFirst());
    }

    @Transactional(readOnly = true)
    public List<DayRef> days(UUID userId, UUID phaseId) {
        List<WorkoutDay> days = dayRepository.findByPhase(phaseId, userId);
        if (days.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = plannedExerciseRepository
                .findByDays(days.stream().map(WorkoutDay::getId).toList(), userId).stream()
                .collect(Collectors.groupingBy(planned -> planned.getDay().getId(), Collectors.counting()));
        return days.stream()
                .map(day -> new DayRef(
                        day.getId(), day.getName(), day.getDayOfWeek(), day.getPosition(),
                        counts.getOrDefault(day.getId(), 0L).intValue()))
                .toList();
    }

    /** Dia com os exercícios planejados. Lança 404 se não for do usuário. */
    @Transactional(readOnly = true)
    public DayPlan dayPlan(UUID userId, UUID dayId) {
        WorkoutDay day = lookup.day(userId, dayId);
        TrainingPhase phase = day.getPhase();
        List<PlannedItem> exercises = plannedExerciseRepository.findByDays(List.of(day.getId()), userId).stream()
                .map(planned -> new PlannedItem(
                        planned.getId(), planned.getExercise(), planned.getPosition(), planned.config()))
                .toList();
        return new DayPlan(
                phase.getPlan().getId(), phase.getPlan().getName(),
                phase.getId(), phase.getName(),
                day.getId(), day.getName(),
                exercises);
    }

    /** Semana do usuário, se pertencer à fase informada. */
    @Transactional(readOnly = true)
    public Optional<WeekRef> week(UUID userId, UUID weekId, UUID phaseId) {
        return weekRepository.findByIdAndUserId(weekId, userId)
                .filter(week -> week.getPhase().getId().equals(phaseId))
                .map(TrainingReader::weekRef);
    }

    /** Semana da fase que contém a data (só quando a fase tem data de início). */
    @Transactional(readOnly = true)
    public Optional<WeekRef> weekOn(UUID userId, UUID phaseId, LocalDate date) {
        TrainingPhase phase = lookup.phase(userId, phaseId);
        if (phase.getStartDate() == null || date.isBefore(phase.getStartDate())) {
            return Optional.empty();
        }
        long number = ChronoUnit.WEEKS.between(phase.getStartDate(), date) + 1;
        if (number > phase.getWeeksCount()) {
            return Optional.empty();
        }
        Map<Integer, TrainingWeek> byNumber = weekRepository.findByPhase(phaseId, userId).stream()
                .collect(Collectors.toMap(TrainingWeek::getWeekNumber, Function.identity()));
        return Optional.ofNullable(byNumber.get((int) number)).map(TrainingReader::weekRef);
    }

    private static PhaseRef phaseRef(TrainingPhase phase) {
        return new PhaseRef(
                phase.getId(), phase.getName(), phase.getPosition(),
                phase.getStartDate(), phase.getEndDate(), phase.getWeeksCount());
    }

    private static WeekRef weekRef(TrainingWeek week) {
        return new WeekRef(
                week.getId(), week.getPhase().getId(), week.getWeekNumber(), week.getStartDate(), week.isDeload());
    }
}
