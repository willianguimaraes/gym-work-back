package com.gymwork.api.workout;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WorkoutObservationRepository extends Repository<WorkoutObservation, UUID> {

    WorkoutObservation save(WorkoutObservation observation);

    void delete(WorkoutObservation observation);

    Optional<WorkoutObservation> findByIdAndUserId(UUID id, UUID userId);

    List<WorkoutObservation> findByUserIdAndSessionIdOrderByObservedAtAsc(UUID userId, UUID sessionId);

    @Query("""
            select o from WorkoutObservation o
            where o.userId = :userId and o.observedAt >= :from and o.observedAt < :to
            order by o.observedAt desc
            """)
    Page<WorkoutObservation> findPeriod(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkoutObservation o where o.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
