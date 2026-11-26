package com.gymwork.api.workout;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ExerciseSetRepository extends Repository<ExerciseSet, UUID> {

    ExerciseSet save(ExerciseSet set);

    void delete(ExerciseSet set);

    @Query("""
            select s from ExerciseSet s join fetch s.sessionExercise e join fetch e.session
            where s.id = :id and s.userId = :userId
            """)
    Optional<ExerciseSet> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<ExerciseSet> findByUserIdAndClientId(UUID userId, UUID clientId);

    @Query("""
            select s from ExerciseSet s
            where s.sessionExercise.id in :sessionExerciseIds and s.userId = :userId
            order by s.setNumber, s.id
            """)
    List<ExerciseSet> findBySessionExercises(
            @Param("sessionExerciseIds") Collection<UUID> sessionExerciseIds, @Param("userId") UUID userId);

    @Query("select coalesce(max(s.setNumber), 0) from ExerciseSet s where s.sessionExercise.id = :sessionExerciseId")
    int maxSetNumber(@Param("sessionExerciseId") UUID sessionExerciseId);

    /** Séries válidas concluídas (trabalho e adicionais) de um exercício da sessão. */
    @Query("""
            select count(s) from ExerciseSet s
            where s.sessionExercise.id = :sessionExerciseId
              and s.status = com.gymwork.api.workout.SetStatus.COMPLETED
              and s.setType in (com.gymwork.api.workout.SetType.WORKING, com.gymwork.api.workout.SetType.EXTRA)
            """)
    long countCompletedSets(@Param("sessionExerciseId") UUID sessionExerciseId);

    /** Séries válidas concluídas por sessão: linhas [sessionId, total]. */
    @Query("""
            select e.session.id, count(s) from ExerciseSet s join s.sessionExercise e
            where e.session.id in :sessionIds
              and s.status = com.gymwork.api.workout.SetStatus.COMPLETED
              and s.setType in (com.gymwork.api.workout.SetType.WORKING, com.gymwork.api.workout.SetType.EXTRA)
            group by e.session.id
            """)
    List<Object[]> countCompletedBySession(@Param("sessionIds") Collection<UUID> sessionIds);
}
