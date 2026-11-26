package com.gymwork.api.workout;

import com.gymwork.api.user.UserDataDeletionHandler;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Apaga observações e sessões (com exercícios e séries) na exclusão de conta. Roda antes dos
 * planejamentos e dos exercícios personalizados, que as sessões referenciam.
 */
@Component
@Order(10)
class WorkoutDataDeletionHandler implements UserDataDeletionHandler {

    private final WorkoutObservationRepository observationRepository;
    private final WorkoutSessionRepository sessionRepository;

    WorkoutDataDeletionHandler(
            WorkoutObservationRepository observationRepository, WorkoutSessionRepository sessionRepository) {
        this.observationRepository = observationRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void deleteUserData(UUID userId) {
        observationRepository.deleteAllByUserId(userId);
        sessionRepository.deleteAllByUserId(userId);
    }
}
