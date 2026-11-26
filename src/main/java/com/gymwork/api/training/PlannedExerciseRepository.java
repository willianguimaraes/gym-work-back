package com.gymwork.api.training;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PlannedExerciseRepository extends Repository<PlannedExercise, UUID> {

    PlannedExercise save(PlannedExercise plannedExercise);

    void delete(PlannedExercise plannedExercise);

    @Query("""
            select p from PlannedExercise p join fetch p.exercise join fetch p.day
            where p.id = :id and p.userId = :userId
            """)
    Optional<PlannedExercise> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select p from PlannedExercise p join fetch p.exercise
            where p.day.id in :dayIds and p.userId = :userId
            order by p.position, p.id
            """)
    List<PlannedExercise> findByDays(@Param("dayIds") Collection<UUID> dayIds, @Param("userId") UUID userId);

    @Query("select coalesce(max(p.position), -1) from PlannedExercise p where p.day.id = :dayId")
    int maxPosition(@Param("dayId") UUID dayId);
}
