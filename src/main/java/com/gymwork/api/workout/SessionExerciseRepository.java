package com.gymwork.api.workout;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SessionExerciseRepository extends Repository<SessionExercise, UUID> {

    SessionExercise save(SessionExercise sessionExercise);

    @Query("""
            select e from SessionExercise e join fetch e.session
            where e.id = :id and e.userId = :userId
            """)
    Optional<SessionExercise> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select e from SessionExercise e
            where e.session.id = :sessionId and e.userId = :userId
            order by e.position, e.id
            """)
    List<SessionExercise> findBySession(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);

    @Query("select coalesce(max(e.position), -1) from SessionExercise e where e.session.id = :sessionId")
    int maxPosition(@Param("sessionId") UUID sessionId);

    /** Abre espaço para inserir um exercício logo depois de {@code position} (substituição). */
    @Query("""
            select e from SessionExercise e
            where e.session.id = :sessionId and e.position > :position
            """)
    List<SessionExercise> findAfter(@Param("sessionId") UUID sessionId, @Param("position") int position);

    /**
     * Última execução de cada exercício antes de {@code before}, ignorando a sessão atual: a
     * execução mais recente que tem ao menos uma série válida concluída (sem aquecimento/ativação).
     */
    @Query(value = """
            select distinct on (se.exercise_id) se.id
            from session_exercises se
            join workout_sessions s on s.id = se.session_id
            where se.user_id = :userId
              and se.exercise_id in (:exerciseIds)
              and se.session_id <> :sessionId
              and s.started_at < :before
              and exists (
                  select 1 from exercise_sets es
                  where es.session_exercise_id = se.id
                    and es.status = 'COMPLETED'
                    and es.set_type not in ('WARMUP', 'ACTIVATION'))
            order by se.exercise_id, s.started_at desc
            """, nativeQuery = true)
    List<UUID> findLastExecutionIds(
            @Param("userId") UUID userId,
            @Param("exerciseIds") Collection<UUID> exerciseIds,
            @Param("sessionId") UUID sessionId,
            @Param("before") Instant before);

    /** Exercícios por sessão (sem contar os substituídos): linhas [sessionId, total]. */
    @Query("""
            select e.session.id, count(e) from SessionExercise e
            where e.session.id in :sessionIds
              and e.status <> com.gymwork.api.workout.SessionExerciseStatus.SUBSTITUTED
            group by e.session.id
            """)
    List<Object[]> countBySession(@Param("sessionIds") Collection<UUID> sessionIds);

    @Query("select e from SessionExercise e join fetch e.session where e.id in :ids")
    List<SessionExercise> findWithSession(@Param("ids") Collection<UUID> ids);
}
