package com.gymwork.api.training;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mantém a estrutura de uma fase: as semanas 1..N (geradas a partir da duração) e a cópia do
 * conteúdo de uma fase para outra (duplicação de fase ou de planejamento).
 */
@Component
class PhaseStructure {

    private final TrainingWeekRepository weekRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;

    PhaseStructure(
            TrainingWeekRepository weekRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository) {
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
    }

    /**
     * Ajusta as semanas à duração da fase: cria as que faltam, remove as excedentes do fim e
     * recalcula as datas de início.
     */
    List<TrainingWeek> syncWeeks(TrainingPhase phase) {
        List<TrainingWeek> existing = weekRepository.findByPhase(phase.getId(), phase.getUserId());
        for (TrainingWeek week : existing) {
            if (week.getWeekNumber() > phase.getWeeksCount()) {
                weekRepository.delete(week);
            } else {
                week.refreshStartDate();
            }
        }
        for (int number = existing.size() + 1; number <= phase.getWeeksCount(); number++) {
            weekRepository.save(new TrainingWeek(phase, number));
        }
        return weekRepository.findByPhase(phase.getId(), phase.getUserId());
    }

    /**
     * Copia para {@code target} (já com as semanas criadas) as marcações de deload das semanas, os
     * dias de treino e os exercícios planejados de {@code source}, na mesma ordem.
     */
    void copyContents(TrainingPhase source, TrainingPhase target) {
        Map<Integer, Boolean> deloadByWeek = new HashMap<>();
        for (TrainingWeek week : weekRepository.findByPhase(source.getId(), source.getUserId())) {
            deloadByWeek.put(week.getWeekNumber(), week.isDeload());
        }
        for (TrainingWeek week : weekRepository.findByPhase(target.getId(), target.getUserId())) {
            week.update(deloadByWeek.getOrDefault(week.getWeekNumber(), false), null);
        }

        List<WorkoutDay> days = dayRepository.findByPhase(source.getId(), source.getUserId());
        if (days.isEmpty()) {
            return;
        }
        Map<UUID, WorkoutDay> copies = new HashMap<>();
        for (WorkoutDay day : days) {
            WorkoutDay copy = new WorkoutDay(target, day.getPosition());
            copy.update(day.getName(), day.getDayOfWeek(), day.getNotes());
            copies.put(day.getId(), dayRepository.save(copy));
        }
        List<PlannedExercise> exercises =
                plannedExerciseRepository.findByDays(days.stream().map(WorkoutDay::getId).toList(), source.getUserId());
        for (PlannedExercise planned : exercises) {
            PlannedExercise copy =
                    new PlannedExercise(copies.get(planned.getDay().getId()), planned.getExercise(), planned.getPosition());
            copy.configure(planned.config());
            plannedExerciseRepository.save(copy);
        }
    }

    /** Nome da cópia ("Nome (cópia)"), respeitando o tamanho máximo do campo. */
    static String copyName(String name, int maxLength) {
        String suffix = " (cópia)";
        String base = name.length() + suffix.length() > maxLength
                ? name.substring(0, maxLength - suffix.length()).trim()
                : name;
        return base + suffix;
    }
}
