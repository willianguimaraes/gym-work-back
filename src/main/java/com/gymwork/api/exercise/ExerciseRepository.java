package com.gymwork.api.exercise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID>, JpaSpecificationExecutor<Exercise> {

    /** Exercício global ou personalizado do próprio usuário. */
    @Query("select e from Exercise e where e.id = :id and (e.ownerId is null or e.ownerId = :userId)")
    Optional<Exercise> findVisible(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Só exercícios personalizados do usuário (os editáveis). */
    Optional<Exercise> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Exercícios visíveis ao usuário com o mesmo nome (sem diferenciar maiúsculas) e equipamento. */
    @Query("""
            select e from Exercise e
            where lower(e.name) = lower(:name) and e.equipment = :equipment
              and (e.ownerId is null or e.ownerId = :userId)
            """)
    List<Exercise> findSameName(
            @Param("userId") UUID userId, @Param("name") String name, @Param("equipment") Equipment equipment);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Exercise e where e.ownerId = :ownerId")
    void deleteAllByOwnerId(@Param("ownerId") UUID ownerId);
}
