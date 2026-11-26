package com.gymwork.api.progression;

import com.gymwork.api.progression.ExecutionRecord.SetRecord;
import com.gymwork.api.workout.ExerciseSet;
import com.gymwork.api.workout.ExerciseSetRepository;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.SessionExercise;
import com.gymwork.api.workout.WorkoutObservation;
import com.gymwork.api.workout.WorkoutObservationRepository;
import com.gymwork.api.workout.WorkoutSession;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converte exercícios de sessões finalizadas em {@link ExecutionRecord} (séries e dor), em lote. */
@Component
class ExecutionLoader {

    /** Desconforto a partir desta intensidade (ou com interrupção) também suspende a sugestão. */
    static final int RELEVANT_DISCOMFORT = 5;

    private final ExerciseSetRepository setRepository;
    private final WorkoutObservationRepository observationRepository;

    ExecutionLoader(ExerciseSetRepository setRepository, WorkoutObservationRepository observationRepository) {
        this.setRepository = setRepository;
        this.observationRepository = observationRepository;
    }

    List<ExecutionRecord> records(UUID userId, List<SessionExercise> exercises) {
        if (exercises.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExerciseSet>> sets = setRepository
                .findBySessionExercises(exercises.stream().map(SessionExercise::getId).toList(), userId).stream()
                .collect(Collectors.groupingBy(set -> set.getSessionExercise().getId()));
        Set<UUID> sessionIds = exercises.stream().map(e -> e.getSession().getId()).collect(Collectors.toSet());
        List<WorkoutObservation> body = observationRepository.findBodyRelatedInSessions(userId, sessionIds);

        Set<UUID> painBySessionExercise = new HashSet<>();
        Set<String> painBySessionAndExercise = new HashSet<>();
        for (WorkoutObservation observation : body) {
            if (!isRelevant(observation)) {
                continue;
            }
            if (observation.getSessionExerciseId() != null) {
                painBySessionExercise.add(observation.getSessionExerciseId());
            } else if (observation.getExerciseId() != null) {
                painBySessionAndExercise.add(observation.getSessionId() + ":" + observation.getExerciseId());
            }
        }

        return exercises.stream().map(exercise -> {
            WorkoutSession session = exercise.getSession();
            UUID exerciseId = exercise.getExercise().getId();
            boolean pain = painBySessionExercise.contains(exercise.getId())
                    || painBySessionAndExercise.contains(session.getId() + ":" + exerciseId);
            List<SetRecord> setRecords = sets.getOrDefault(exercise.getId(), List.of()).stream()
                    .map(set -> new SetRecord(set.getSetType(), set.getStatus(), set.getLoadKg(), set.getReps(), set.getRir()))
                    .toList();
            return new ExecutionRecord(
                    session.getId(),
                    exercise.getId(),
                    exerciseId,
                    session.getStartedAt(),
                    session.getDayName(),
                    session.getPhaseId(),
                    session.getPhaseName(),
                    session.getWeekNumber(),
                    session.isDeloadWeek(),
                    exercise.getStatus(),
                    exercise.getPlannedSets(),
                    exercise.getRepsMin(),
                    exercise.getRepsMax(),
                    exercise.getTargetRir(),
                    setRecords,
                    pain);
        }).toList();
    }

    /** Dor sempre conta; desconforto conta se intenso ou se interrompeu o exercício. */
    static boolean isRelevant(WorkoutObservation observation) {
        if (observation.getCategory() == ObservationCategory.PAIN) {
            return true;
        }
        return observation.isInterrupted()
                || (observation.getIntensity() != null && observation.getIntensity() >= RELEVANT_DISCOMFORT);
    }
}
