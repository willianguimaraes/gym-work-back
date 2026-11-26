package com.gymwork.api.workout;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends Repository<WorkoutSession, UUID> {

    WorkoutSession save(WorkoutSession session);

    void delete(WorkoutSession session);

    Optional<WorkoutSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<WorkoutSession> findByUserIdAndStatus(UUID userId, SessionStatus status);

    /** Histórico, mais recentes primeiro. {@code from}/{@code to} limitam o início da sessão. */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId and s.startedAt >= :from and s.startedAt < :to
            order by s.startedAt desc
            """)
    Page<WorkoutSession> findHistory(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** Sessões de uma semana do plano (relatórios), em ordem cronológica. */
    List<WorkoutSession> findByUserIdAndWeekIdOrderByStartedAtAsc(UUID userId, UUID weekId);

    /** Sessões de uma fase do plano (relatórios), em ordem cronológica. */
    List<WorkoutSession> findByUserIdAndPhaseIdOrderByStartedAtAsc(UUID userId, UUID phaseId);

    /** Último início por dia de treino: linhas [dayId, startedAt]. */
    @Query("""
            select s.workoutDayId, max(s.startedAt) from WorkoutSession s
            where s.userId = :userId and s.workoutDayId in :dayIds
            group by s.workoutDayId
            """)
    List<Object[]> lastStartByDay(@Param("userId") UUID userId, @Param("dayIds") Collection<UUID> dayIds);

    /** Apaga as sessões do usuário; o banco apaga exercícios, séries e observações delas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkoutSession s where s.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
