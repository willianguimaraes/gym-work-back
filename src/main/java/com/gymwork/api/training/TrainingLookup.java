package com.gymwork.api.training;

import com.gymwork.api.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Busca recursos do planejamento sempre por (id, usuário). Recurso de outro usuário responde 404,
 * igual a um que não existe, para não revelar que o ID existe.
 */
@Component
class TrainingLookup {

    private final TrainingPlanRepository planRepository;
    private final TrainingPhaseRepository phaseRepository;
    private final TrainingWeekRepository weekRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;

    TrainingLookup(
            TrainingPlanRepository planRepository,
            TrainingPhaseRepository phaseRepository,
            TrainingWeekRepository weekRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository) {
        this.planRepository = planRepository;
        this.phaseRepository = phaseRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
    }

    TrainingPlan plan(UUID userId, UUID planId) {
        return planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Planejamento não encontrado"));
    }

    TrainingPhase phase(UUID userId, UUID phaseId) {
        return phaseRepository.findByIdAndUserId(phaseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Fase não encontrada"));
    }

    TrainingWeek week(UUID userId, UUID weekId) {
        return weekRepository.findByIdAndUserId(weekId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Semana não encontrada"));
    }

    WorkoutDay day(UUID userId, UUID dayId) {
        return dayRepository.findByIdAndUserId(dayId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Dia de treino não encontrado"));
    }

    PlannedExercise plannedExercise(UUID userId, UUID plannedExerciseId) {
        return plannedExerciseRepository.findByIdAndUserId(plannedExerciseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercício do plano não encontrado"));
    }
}
