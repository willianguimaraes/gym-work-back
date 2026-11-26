package com.gymwork.api.training;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TrainingPhaseRepository extends Repository<TrainingPhase, UUID> {

    TrainingPhase save(TrainingPhase phase);

    void delete(TrainingPhase phase);

    @Query("select f from TrainingPhase f join fetch f.plan where f.id = :id and f.userId = :userId")
    Optional<TrainingPhase> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select f from TrainingPhase f where f.plan.id = :planId and f.userId = :userId order by f.position, f.id")
    List<TrainingPhase> findByPlan(@Param("planId") UUID planId, @Param("userId") UUID userId);

    /** Por plano do usuário: linhas [planId, quantidade de fases, soma das semanas]. */
    @Query("""
            select f.plan.id, count(f), coalesce(sum(f.weeksCount), 0) from TrainingPhase f
            where f.userId = :userId group by f.plan.id
            """)
    List<Object[]> totalsByPlan(@Param("userId") UUID userId);

    @Query("select coalesce(max(f.position), -1) from TrainingPhase f where f.plan.id = :planId")
    int maxPosition(@Param("planId") UUID planId);
}
