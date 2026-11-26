package com.gymwork.api.assessment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProgressPhotoRepository extends Repository<ProgressPhoto, UUID> {

    ProgressPhoto save(ProgressPhoto photo);

    void delete(ProgressPhoto photo);

    @Query("select p from ProgressPhoto p join fetch p.assessment where p.id = :id and p.userId = :userId")
    Optional<ProgressPhoto> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select p from ProgressPhoto p
            where p.assessment.id in :assessmentIds and p.userId = :userId
            order by p.position, p.createdAt
            """)
    List<ProgressPhoto> findByAssessments(
            @Param("assessmentIds") Collection<UUID> assessmentIds, @Param("userId") UUID userId);

    @Query("select count(p) from ProgressPhoto p where p.assessment.id = :assessmentId")
    long countByAssessment(@Param("assessmentId") UUID assessmentId);

    @Query("select p.storageKey from ProgressPhoto p where p.userId = :userId")
    List<String> findStorageKeysByUserId(@Param("userId") UUID userId);
}
