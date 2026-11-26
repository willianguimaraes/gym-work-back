package com.gymwork.api.training;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.training.dto.DayRequest;
import com.gymwork.api.training.dto.DayResponse;
import com.gymwork.api.training.dto.PhaseResponse;
import com.gymwork.api.training.dto.PlannedExerciseRequest;
import com.gymwork.api.training.dto.PlannedExerciseResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dias de treino da fase e os exercícios planejados de cada dia. */
@Service
public class WorkoutDayService {

    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;
    private final ExerciseService exerciseService;
    private final TrainingLookup lookup;
    private final TrainingViews views;

    public WorkoutDayService(
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository,
            ExerciseService exerciseService,
            TrainingLookup lookup,
            TrainingViews views) {
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
        this.exerciseService = exerciseService;
        this.lookup = lookup;
        this.views = views;
    }

    // ---------------------------------------------------------------------------- dias

    @Transactional
    public DayResponse create(UUID userId, UUID phaseId, DayRequest request) {
        TrainingPhase phase = lookup.phase(userId, phaseId);
        WorkoutDay day = new WorkoutDay(phase, dayRepository.maxPosition(phase.getId()) + 1);
        apply(day, request);
        return views.day(dayRepository.save(day));
    }

    @Transactional(readOnly = true)
    public DayResponse get(UUID userId, UUID dayId) {
        return views.day(lookup.day(userId, dayId));
    }

    @Transactional
    public DayResponse update(UUID userId, UUID dayId, DayRequest request) {
        WorkoutDay day = lookup.day(userId, dayId);
        apply(day, request);
        return views.day(day);
    }

    @Transactional
    public void delete(UUID userId, UUID dayId) {
        dayRepository.delete(lookup.day(userId, dayId));
    }

    @Transactional
    public PhaseResponse reorderDays(UUID userId, UUID phaseId, List<UUID> ids) {
        TrainingPhase phase = lookup.phase(userId, phaseId);
        reorder(dayRepository.findByPhase(phase.getId(), userId), ids, WorkoutDay::getId, WorkoutDay::moveTo);
        return views.phase(phase);
    }

    // ---------------------------------------------------------------------------- exercícios do dia

    @Transactional
    public PlannedExerciseResponse addExercise(UUID userId, UUID dayId, PlannedExerciseRequest request) {
        WorkoutDay day = lookup.day(userId, dayId);
        if (request.exerciseId() == null) {
            throw new InvalidFieldException("exerciseId", "Escolha um exercício");
        }
        Exercise exercise = exerciseService.requireUsable(userId, request.exerciseId(), "exerciseId");
        PlannedExercise planned =
                new PlannedExercise(day, exercise, plannedExerciseRepository.maxPosition(day.getId()) + 1);
        planned.configure(config(request));
        return PlannedExerciseResponse.from(plannedExerciseRepository.save(planned));
    }

    @Transactional
    public PlannedExerciseResponse updateExercise(UUID userId, UUID plannedExerciseId, PlannedExerciseRequest request) {
        PlannedExercise planned = lookup.plannedExercise(userId, plannedExerciseId);
        planned.configure(config(request));
        return PlannedExerciseResponse.from(planned);
    }

    @Transactional
    public void removeExercise(UUID userId, UUID plannedExerciseId) {
        plannedExerciseRepository.delete(lookup.plannedExercise(userId, plannedExerciseId));
    }

    /** Troca o exercício mantendo a configuração (séries, reps, descanso, carga, RIR) e a posição. */
    @Transactional
    public PlannedExerciseResponse replaceExercise(UUID userId, UUID plannedExerciseId, UUID exerciseId) {
        PlannedExercise planned = lookup.plannedExercise(userId, plannedExerciseId);
        planned.replaceExercise(exerciseService.requireUsable(userId, exerciseId, "exerciseId"));
        return PlannedExerciseResponse.from(planned);
    }

    @Transactional
    public DayResponse reorderExercises(UUID userId, UUID dayId, List<UUID> ids) {
        WorkoutDay day = lookup.day(userId, dayId);
        reorder(
                plannedExerciseRepository.findByDays(List.of(day.getId()), userId),
                ids, PlannedExercise::getId, PlannedExercise::moveTo);
        return views.day(day);
    }

    // ---------------------------------------------------------------------------- apoio

    private static void apply(WorkoutDay day, DayRequest request) {
        day.update(request.name().trim(), request.dayOfWeek(), TrainingText.blankToNull(request.notes()));
    }

    private static PlannedExerciseConfig config(PlannedExerciseRequest request) {
        if (request.repsMin() > request.repsMax()) {
            throw new InvalidFieldException("repsMax", "As repetições máximas devem ser maiores ou iguais às mínimas");
        }
        return new PlannedExerciseConfig(
                request.sets(),
                request.repsMin(),
                request.repsMax(),
                request.restSeconds(),
                request.suggestedLoadKg(),
                request.targetRir(),
                TrainingText.blankToNull(request.notes()));
    }

    /** Aplica a nova ordem; a lista precisa ter exatamente os itens atuais, sem repetição. */
    static <T> void reorder(List<T> items, List<UUID> ids, Function<T, UUID> idOf, ObjIntConsumer<T> moveTo) {
        Map<UUID, T> byId = items.stream().collect(Collectors.toMap(idOf, Function.identity()));
        if (ids.size() != items.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new InvalidFieldException("ids", "A nova ordem deve conter todos os itens, sem repetição");
        }
        for (int position = 0; position < ids.size(); position++) {
            moveTo.accept(byId.get(ids.get(position)), position);
        }
    }
}
