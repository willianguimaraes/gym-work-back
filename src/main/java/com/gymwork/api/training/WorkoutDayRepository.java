package com.gymwork.api.training;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WorkoutDayRepository extends Repository<WorkoutDay, UUID> {

    WorkoutDay save(WorkoutDay day);

    void delete(WorkoutDay day);

    @Query("""
            select d from WorkoutDay d join fetch d.phase f join fetch f.plan
            where d.id = :id and d.userId = :userId
            """)
    Optional<WorkoutDay> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select d from WorkoutDay d where d.phase.id = :phaseId and d.userId = :userId order by d.position, d.id")
    List<WorkoutDay> findByPhase(@Param("phaseId") UUID phaseId, @Param("userId") UUID userId);

    @Query("select coalesce(max(d.position), -1) from WorkoutDay d where d.phase.id = :phaseId")
    int maxPosition(@Param("phaseId") UUID phaseId);

    /** Quantidade de dias por fase do plano: linhas [phaseId, total]. */
    @Query("""
            select d.phase.id, count(d) from WorkoutDay d
            where d.phase.plan.id = :planId and d.userId = :userId
            group by d.phase.id
            """)
    List<Object[]> countByPhaseOfPlan(@Param("planId") UUID planId, @Param("userId") UUID userId);
}
