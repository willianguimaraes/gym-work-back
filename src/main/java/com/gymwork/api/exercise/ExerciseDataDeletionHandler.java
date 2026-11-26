package com.gymwork.api.exercise;

import com.gymwork.api.user.UserDataDeletionHandler;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Apaga os exercícios personalizados na exclusão de conta (depois dos planos que os usam). */
@Component
@Order(100)
class ExerciseDataDeletionHandler implements UserDataDeletionHandler {

    private final ExerciseRepository exerciseRepository;

    ExerciseDataDeletionHandler(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public void deleteUserData(UUID userId) {
        exerciseRepository.deleteAllByOwnerId(userId);
    }
}
