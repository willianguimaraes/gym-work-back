package com.gymwork.api.training;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TrainingWeekRepository extends Repository<TrainingWeek, UUID> {

    TrainingWeek save(TrainingWeek week);

    void delete(TrainingWeek week);

    @Query("select w from TrainingWeek w join fetch w.phase where w.id = :id and w.userId = :userId")
    Optional<TrainingWeek> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select w from TrainingWeek w where w.phase.id = :phaseId and w.userId = :userId order by w.weekNumber")
    List<TrainingWeek> findByPhase(@Param("phaseId") UUID phaseId, @Param("userId") UUID userId);
}
