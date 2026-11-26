package com.gymwork.api.workout;

import com.gymwork.api.workout.dto.LastExecutionResponse;
import com.gymwork.api.workout.dto.LastExecutionResponse.LastSet;
import com.gymwork.api.workout.dto.ObservationResponse;
import com.gymwork.api.workout.dto.SessionExerciseResponse;
import com.gymwork.api.workout.dto.SessionResponse;
import com.gymwork.api.workout.dto.SessionSummaryResponse;
import com.gymwork.api.workout.dto.SetResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Monta as respostas da execução com poucas consultas (sem N+1). */
@Component
class SessionViews {

    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseSetRepository setRepository;
    private final WorkoutObservationRepository observationRepository;

    SessionViews(
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseSetRepository setRepository,
            WorkoutObservationRepository observationRepository) {
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.setRepository = setRepository;
        this.observationRepository = observationRepository;
    }

    SessionResponse session(WorkoutSession session) {
        UUID userId = session.getUserId();
        List<SessionExercise> exercises = sessionExerciseRepository.findBySession(session.getId(), userId);
        Map<UUID, List<ExerciseSet>> setsByExercise = setsByExercise(exercises, userId);
        Map<UUID, LastExecutionResponse> lastByExercise = lastExecutions(session, exercises);

        int plannedSets = exercises.stream()
                .filter(exercise -> exercise.getStatus() != SessionExerciseStatus.SUBSTITUTED)
                .map(SessionExercise::getPlannedSets)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int completedSets = (int) setsByExercise.values().stream()
                .flatMap(List::stream)
                .filter(ExerciseSet::countsAsCompletedSet)
                .count();

        List<SessionExerciseResponse> exerciseResponses = exercises.stream()
                .map(exercise -> exercise(
                        exercise,
                        setsByExercise.getOrDefault(exercise.getId(), List.of()),
                        lastByExercise.get(exercise.getExercise().getId())))
                .toList();
        List<ObservationResponse> observations = observationRepository
                .findByUserIdAndSessionIdOrderByObservedAtAsc(userId, session.getId()).stream()
                .map(ObservationResponse::from)
                .toList();

        return new SessionResponse(
                session.getId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getNotes(),
                session.getPlanId(),
                session.getPhaseId(),
                session.getWeekId(),
                session.getWorkoutDayId(),
                session.getPlanName(),
                session.getPhaseName(),
                session.getWeekNumber(),
                session.isDeloadWeek(),
                session.getDayName(),
                plannedSets,
                completedSets,
                exerciseResponses,
                observations);
    }

    /** Exercício isolado (respostas de pular/substituir), sem a última execução. */
    SessionExerciseResponse exercise(SessionExercise exercise) {
        List<ExerciseSet> sets = setsByExercise(List.of(exercise), exercise.getUserId())
                .getOrDefault(exercise.getId(), List.of());
        return exercise(exercise, sets, null);
    }

    List<SessionSummaryResponse> summaries(List<WorkoutSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = sessions.stream().map(WorkoutSession::getId).toList();
        Map<UUID, Long> exercises = counts(sessionExerciseRepository.countBySession(ids));
        Map<UUID, Long> sets = counts(setRepository.countCompletedBySession(ids));
        return sessions.stream()
                .map(session -> new SessionSummaryResponse(
                        session.getId(),
                        session.getStatus(),
                        session.getStartedAt(),
                        session.getFinishedAt(),
                        session.getPlanName(),
                        session.getPhaseName(),
                        session.getWeekNumber(),
                        session.getDayName(),
                        exercises.getOrDefault(session.getId(), 0L).intValue(),
                        sets.getOrDefault(session.getId(), 0L).intValue()))
                .toList();
    }

    SessionSummaryResponse summary(WorkoutSession session) {
        return summaries(List.of(session)).get(0);
    }

    private Map<UUID, List<ExerciseSet>> setsByExercise(List<SessionExercise> exercises, UUID userId) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        return setRepository.findBySessionExercises(exercises.stream().map(SessionExercise::getId).toList(), userId)
                .stream()
                .collect(Collectors.groupingBy(set -> set.getSessionExercise().getId()));
    }

    /** Última execução de cada exercício antes desta sessão, com as séries válidas concluídas. */
    private Map<UUID, LastExecutionResponse> lastExecutions(WorkoutSession session, List<SessionExercise> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        UUID userId = session.getUserId();
        List<UUID> exerciseIds = exercises.stream().map(exercise -> exercise.getExercise().getId()).distinct().toList();
        List<UUID> lastIds = sessionExerciseRepository.findLastExecutionIds(
                userId, exerciseIds, session.getId(), session.getStartedAt());
        if (lastIds.isEmpty()) {
            return Map.of();
        }
        List<SessionExercise> previous = sessionExerciseRepository.findWithSession(lastIds);
        Map<UUID, List<ExerciseSet>> setsByPrevious = setsByExercise(previous, userId);
        Map<UUID, LastExecutionResponse> result = new HashMap<>();
        for (SessionExercise last : previous) {
            List<LastSet> sets = setsByPrevious.getOrDefault(last.getId(), List.of()).stream()
                    .filter(set -> set.getStatus() == SetStatus.COMPLETED && !set.getSetType().isPreparation())
                    .map(set -> new LastSet(set.getSetType(), set.getLoadKg(), set.getReps(), set.getRir()))
                    .toList();
            result.put(last.getExercise().getId(),
                    new LastExecutionResponse(last.getSession().getId(), last.getSession().getStartedAt(), sets));
        }
        return result;
    }

    private static SessionExerciseResponse exercise(
            SessionExercise exercise, List<ExerciseSet> sets, LastExecutionResponse last) {
        return new SessionExerciseResponse(
                exercise.getId(),
                exercise.getPosition(),
                exercise.getExercise().getId(),
                exercise.getExerciseName(),
                exercise.getEquipment(),
                exercise.getMuscleGroup(),
                exercise.getPlannedExerciseId(),
                exercise.getStatus(),
                exercise.getSubstitutedById(),
                exercise.getNotes(),
                exercise.getPlannedSets(),
                exercise.getRepsMin(),
                exercise.getRepsMax(),
                exercise.getRestSeconds(),
                exercise.getTargetRir(),
                exercise.getReferenceLoadKg(),
                sets.stream().map(SetResponse::from).toList(),
                last);
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
