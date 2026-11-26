package com.gymwork.api.training;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TrainingPlanRepository extends Repository<TrainingPlan, UUID> {

    TrainingPlan save(TrainingPlan plan);

    void delete(TrainingPlan plan);

    Optional<TrainingPlan> findByIdAndUserId(UUID id, UUID userId);

    List<TrainingPlan> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TrainingPlan> findByUserIdAndStatus(UUID userId, PlanStatus status);

    /** Apaga os planos do usuário; o banco apaga fases, semanas, dias e exercícios em cascata. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TrainingPlan p where p.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
