package com.gymwork.api.workout;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.training.TrainingReader;
import com.gymwork.api.training.TrainingReader.DayPlan;
import com.gymwork.api.training.TrainingReader.PhaseRef;
import com.gymwork.api.training.TrainingReader.PlanRef;
import com.gymwork.api.training.TrainingReader.PlannedItem;
import com.gymwork.api.training.TrainingReader.WeekRef;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.dto.SessionExerciseResponse;
import com.gymwork.api.workout.dto.SessionResponse;
import com.gymwork.api.workout.dto.SessionSummaryResponse;
import com.gymwork.api.workout.dto.StartOptionsResponse;
import com.gymwork.api.workout.dto.StartOptionsResponse.DayOption;
import com.gymwork.api.workout.dto.StartSessionRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sessões de treino: iniciar (com snapshot do plano), recuperar, listar, finalizar e descartar. */
@Service
public class SessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final TrainingReader trainingReader;
    private final ExerciseService exerciseService;
    private final WorkoutLookup lookup;
    private final SessionViews views;
    private final UserTime userTime;
    private final Clock clock;

    public SessionService(
            WorkoutSessionRepository sessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            TrainingReader trainingReader,
            ExerciseService exerciseService,
            WorkoutLookup lookup,
            SessionViews views,
            UserTime userTime,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.trainingReader = trainingReader;
        this.exerciseService = exerciseService;
        this.lookup = lookup;
        this.views = views;
        this.userTime = userTime;
        this.clock = clock;
    }

    /**
     * Inicia uma sessão. A partir de um dia do plano, copia os exercícios e a configuração
     * planejada; sem dia, é um treino avulso. Só pode haver uma sessão em andamento.
     */
    @Transactional
    public SessionResponse start(UUID userId, StartSessionRequest request) {
        sessionRepository.findByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS).ifPresent(current -> {
            throw new SessionInProgressException(current.getId());
        });
        WorkoutSession session = new WorkoutSession(userId, clock.instant());
        if (request.workoutDayId() == null) {
            if (request.weekId() != null) {
                throw new InvalidFieldException("weekId", "Informe o dia de treino da semana");
            }
            return views.session(sessionRepository.save(session));
        }

        DayPlan day = trainingReader.dayPlan(userId, request.workoutDayId());
        Optional<WeekRef> week = request.weekId() == null
                ? trainingReader.weekOn(userId, day.phaseId(), userTime.today(userId))
                : Optional.of(trainingReader.week(userId, request.weekId(), day.phaseId())
                        .orElseThrow(() -> new InvalidFieldException("weekId", "Semana não encontrada nesta fase")));
        session.linkToPlan(new SessionPlanContext(
                day.planId(), day.planName(), day.phaseId(), day.phaseName(), day.dayId(), day.dayName(),
                week.map(WeekRef::id).orElse(null),
                week.map(WeekRef::weekNumber).orElse(null),
                week.map(WeekRef::deload).orElse(false)));
        sessionRepository.save(session);

        for (PlannedItem item : day.exercises()) {
            SessionExercise exercise = new SessionExercise(session, item.exercise(), item.position());
            var config = item.config();
            exercise.copyPlan(item.plannedExerciseId(), new PlannedSnapshot(
                    config.sets(), config.repsMin(), config.repsMax(), config.restSeconds(),
                    config.targetRir(), config.suggestedLoadKg()));
            sessionExerciseRepository.save(exercise);
        }
        return views.session(session);
    }

    /** Sessão em andamento, para retomar depois de fechar ou recarregar a página. */
    @Transactional(readOnly = true)
    public Optional<SessionResponse> current(UUID userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS).map(views::session);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID userId, UUID sessionId) {
        return views.session(lookup.session(userId, sessionId));
    }

    /** Histórico, mais recentes primeiro. Datas no fuso do usuário; o fim é inclusivo. */
    @Transactional(readOnly = true)
    public PageResponse<SessionSummaryResponse> history(UUID userId, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidFieldException("to", "A data final deve ser igual ou posterior à inicial");
        }
        ZoneId zone = userTime.zone(userId);
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay(zone).toInstant();
        var page = sessionRepository.findHistory(userId, start, end, pageable);
        List<SessionSummaryResponse> content = views.summaries(page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Finaliza mesmo com exercícios ou séries não feitos: o que não foi registrado fica como não feito. */
    @Transactional
    public SessionResponse finish(UUID userId, UUID sessionId, String notes) {
        WorkoutSession session = lookup.session(userId, sessionId);
        if (!session.isInProgress()) {
            throw new InvalidFieldException("status", "A sessão já foi finalizada");
        }
        session.finish(clock.instant(), blankToNull(notes));
        return views.session(session);
    }

    @Transactional
    public SessionResponse updateNotes(UUID userId, UUID sessionId, String notes) {
        WorkoutSession session = lookup.session(userId, sessionId);
        session.updateNotes(blankToNull(notes));
        return views.session(session);
    }

    /** Descarta a sessão com séries e observações dela. */
    @Transactional
    public void discard(UUID userId, UUID sessionId) {
        sessionRepository.delete(lookup.session(userId, sessionId));
    }

    /** Exercício extra, fora do plano, no fim da sessão. */
    @Transactional
    public SessionExerciseResponse addExercise(UUID userId, UUID sessionId, UUID exerciseId) {
        WorkoutSession session = lookup.session(userId, sessionId);
        Exercise exercise = exerciseService.requireUsable(userId, exerciseId, "exerciseId");
        SessionExercise added = new SessionExercise(
                session, exercise, sessionExerciseRepository.maxPosition(session.getId()) + 1);
        return views.exercise(sessionExerciseRepository.save(added));
    }

    /**
     * Tela "Treinar": sessão em andamento ou os dias da fase atual do planejamento ativo (ou da fase
     * escolhida), com a semana de hoje e quando cada dia foi feito pela última vez.
     */
    @Transactional(readOnly = true)
    public StartOptionsResponse startOptions(UUID userId, UUID phaseId) {
        LocalDate today = userTime.today(userId);
        SessionSummaryResponse current = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS)
                .map(views::summary)
                .orElse(null);
        Optional<PlanRef> plan = trainingReader.activePlan(userId);
        if (plan.isEmpty()) {
            return new StartOptionsResponse(current, today, null, List.of(), null, null, List.of());
        }
        List<PhaseRef> phases = trainingReader.phases(userId, plan.get().id());
        Optional<PhaseRef> phase = phaseId == null
                ? trainingReader.phaseOn(userId, plan.get().id(), today)
                : phases.stream().filter(candidate -> candidate.id().equals(phaseId)).findFirst();
        if (phase.isEmpty()) {
            if (phaseId != null) {
                throw new InvalidFieldException("phaseId", "Fase não encontrada no planejamento ativo");
            }
            return new StartOptionsResponse(current, today, plan.get(), phases, null, null, List.of());
        }
        WeekRef week = trainingReader.weekOn(userId, phase.get().id(), today).orElse(null);
        var days = trainingReader.days(userId, phase.get().id());
        Map<UUID, Instant> lastByDay = new HashMap<>();
        if (!days.isEmpty()) {
            for (Object[] row : sessionRepository.lastStartByDay(userId, days.stream().map(d -> d.id()).toList())) {
                lastByDay.put((UUID) row[0], (Instant) row[1]);
            }
        }
        List<DayOption> options = days.stream()
                .map(day -> new DayOption(
                        day.id(), day.name(), day.dayOfWeek(), day.exercisesCount(), lastByDay.get(day.id())))
                .toList();
        return new StartOptionsResponse(current, today, plan.get(), phases, phase.get(), week, options);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
