package com.gymwork.api.workout;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Busca recursos da execução sempre por (id, usuário); de outro usuário responde 404. */
@Component
class WorkoutLookup {

    private final WorkoutSessionRepository sessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseSetRepository setRepository;
    private final WorkoutObservationRepository observationRepository;

    WorkoutLookup(
            WorkoutSessionRepository sessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseSetRepository setRepository,
            WorkoutObservationRepository observationRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.setRepository = setRepository;
        this.observationRepository = observationRepository;
    }

    WorkoutSession session(UUID userId, UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada"));
    }

    SessionExercise sessionExercise(UUID userId, UUID sessionExerciseId) {
        return sessionExerciseRepository.findByIdAndUserId(sessionExerciseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercício da sessão não encontrado"));
    }

    ExerciseSet set(UUID userId, UUID setId) {
        return setRepository.findByIdAndUserId(setId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Série não encontrada"));
    }

    WorkoutObservation observation(UUID userId, UUID observationId) {
        return observationRepository.findByIdAndUserId(observationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Observação não encontrada"));
    }

    /** Referência opcional vinda do corpo da requisição: inválida vira erro de campo (400). */
    WorkoutSession sessionField(UUID userId, UUID sessionId, String field) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new InvalidFieldException(field, "Sessão não encontrada"));
    }

    SessionExercise sessionExerciseField(UUID userId, UUID sessionExerciseId, String field) {
        return sessionExerciseRepository.findByIdAndUserId(sessionExerciseId, userId)
                .orElseThrow(() -> new InvalidFieldException(field, "Exercício da sessão não encontrado"));
    }
}
