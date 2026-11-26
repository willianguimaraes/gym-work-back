package com.gymwork.api.training;

import com.gymwork.api.user.UserDataDeletionHandler;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Apaga os planejamentos na exclusão de conta (antes dos exercícios personalizados). */
@Component
@Order(50)
class TrainingDataDeletionHandler implements UserDataDeletionHandler {

    private final TrainingPlanRepository planRepository;

    TrainingDataDeletionHandler(TrainingPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void deleteUserData(UUID userId) {
        planRepository.deleteAllByUserId(userId);
    }
}
