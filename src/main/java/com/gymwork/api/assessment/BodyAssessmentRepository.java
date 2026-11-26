package com.gymwork.api.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BodyAssessmentRepository extends Repository<BodyAssessment, UUID> {

    BodyAssessment save(BodyAssessment assessment);

    void delete(BodyAssessment assessment);

    Optional<BodyAssessment> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select a from BodyAssessment a where a.userId = :userId
            order by a.assessedOn desc, a.createdAt desc
            """)
    Page<BodyAssessment> findHistory(@Param("userId") UUID userId, Pageable pageable);

    /** Todas, mais antigas primeiro (gráficos de evolução). */
    @Query("""
            select a from BodyAssessment a where a.userId = :userId
            order by a.assessedOn, a.createdAt
            """)
    List<BodyAssessment> findAllChronological(@Param("userId") UUID userId);

    /** Avaliações num intervalo de datas (inclusive), mais antigas primeiro. */
    @Query("""
            select a from BodyAssessment a
            where a.userId = :userId and a.assessedOn >= :from and a.assessedOn <= :to
            order by a.assessedOn, a.createdAt
            """)
    List<BodyAssessment> findBetween(
            @Param("userId") UUID userId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    /** Apaga uma avaliação; o banco apaga medidas e fotos (registros) em cascata. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BodyAssessment a where a.id = :id and a.userId = :userId")
    void deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Apaga as avaliações do usuário; o banco apaga medidas e fotos (registros) em cascata. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BodyAssessment a where a.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
