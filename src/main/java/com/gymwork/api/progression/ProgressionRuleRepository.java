package com.gymwork.api.progression;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProgressionRuleRepository extends Repository<ProgressionRule, UUID> {

    ProgressionRule save(ProgressionRule rule);

    void delete(ProgressionRule rule);

    Optional<ProgressionRule> findByUserIdAndExerciseId(UUID userId, UUID exerciseId);

    List<ProgressionRule> findByUserIdAndExerciseIdIn(UUID userId, Collection<UUID> exerciseIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProgressionRule r where r.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
