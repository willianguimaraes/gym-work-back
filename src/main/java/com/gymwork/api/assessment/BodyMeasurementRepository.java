package com.gymwork.api.assessment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BodyMeasurementRepository extends Repository<BodyMeasurement, UUID> {

    BodyMeasurement save(BodyMeasurement measurement);

    void delete(BodyMeasurement measurement);

    @Query("""
            select m from BodyMeasurement m
            where m.assessment.id in :assessmentIds and m.userId = :userId
            order by m.type
            """)
    List<BodyMeasurement> findByAssessments(
            @Param("assessmentIds") Collection<UUID> assessmentIds, @Param("userId") UUID userId);
}
