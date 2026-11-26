package com.gymwork.api.report;

import com.gymwork.api.assessment.AssessmentService;
import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import com.gymwork.api.training.TrainingReader;
import com.gymwork.api.training.TrainingReader.DayRef;
import com.gymwork.api.training.TrainingReader.PhaseDetails;
import com.gymwork.api.training.TrainingReader.WeekRef;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.ExerciseSet;
import com.gymwork.api.workout.ExerciseSetRepository;
import com.gymwork.api.workout.SessionExercise;
import com.gymwork.api.workout.SessionExerciseRepository;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetType;
import com.gymwork.api.workout.WorkoutObservation;
import com.gymwork.api.workout.WorkoutObservationRepository;
import com.gymwork.api.workout.WorkoutSession;
import com.gymwork.api.workout.WorkoutSessionRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o {@link Report} de uma sessão, semana ou fase, sempre filtrando pelo usuário. */
@Service
public class ReportLoader {

    private final WorkoutSessionRepository sessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseSetRepository setRepository;
    private final WorkoutObservationRepository observationRepository;
    private final TrainingReader trainingReader;
    private final AssessmentService assessmentService;
    private final UserTime userTime;

    public ReportLoader(
            WorkoutSessionRepository sessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseSetRepository setRepository,
            WorkoutObservationRepository observationRepository,
            TrainingReader trainingReader,
            AssessmentService assessmentService,
            UserTime userTime) {
        this.sessionRepository = sessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.setRepository = setRepository;
        this.observationRepository = observationRepository;
        this.trainingReader = trainingReader;
        this.assessmentService = assessmentService;
        this.userTime = userTime;
    }

    /**
     * Relatório carregado com o que a análise precisa: os exercícios executados (entidades), a fase
     * de referência do histórico e o fim do período.
     */
    public record Loaded(Report report, List<SessionExercise> exercises, UUID phaseId, Instant until) {
    }

    @Transactional(readOnly = true)
    public Loaded session(UUID userId, UUID sessionId) {
        WorkoutSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada"));
        ZoneId zone = userTime.zone(userId);
        Data data = load(userId, List.of(session), zone);
        LocalDate date = date(session, zone);

        LocalDate weekStart = null;
        if (session.getWeekId() != null) {
            weekStart = trainingReader.week(userId, session.getWeekId()).startDate();
        }
        LocalDate weekEnd = weekStart == null ? null : weekStart.plusDays(6);
        BigDecimal weight = weekStart == null ? null : latestWeight(assessments(userId, weekStart, weekEnd), weekStart, weekEnd);
        Report.Day day = new Report.Day(
                date.getDayOfWeek(), dayName(session), session.getWorkoutDayId(), data.sessions.get(session.getId()));
        Report.Week week = new Report.Week(
                session.getWeekNumber(), session.isDeloadWeek(), weekStart, weekEnd, weight, List.of(day));
        Report report = new Report(
                ReportScope.SESSION, session.getPlanName(), session.getPhaseName(), date, date, List.of(week));
        return new Loaded(report, data.exercises, session.getPhaseId(), until(session));
    }

    @Transactional(readOnly = true)
    public Loaded week(UUID userId, UUID weekId) {
        WeekRef week = trainingReader.week(userId, weekId);
        PhaseDetails phase = trainingReader.phaseDetails(userId, week.phaseId());
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(userId);
        List<WorkoutSession> sessions = sessionRepository.findByUserIdAndWeekIdOrderByStartedAtAsc(userId, weekId);
        Data data = load(userId, sessions, zone);
        List<DayRef> days = trainingReader.days(userId, week.phaseId());

        LocalDate from = week.startDate() != null ? week.startDate() : firstDate(sessions, zone);
        LocalDate to = week.startDate() != null ? week.startDate().plusDays(6) : lastDate(sessions, zone);
        List<TrendPoint> assessments = from == null ? List.of() : assessments(userId, from, to);
        Report.Week block = week(week.weekNumber(), week.deload(), week.startDate(), sessions, days, data, assessments, zone, today);
        Report report = new Report(ReportScope.WEEK, phase.planName(), phase.phase().name(), from, to, List.of(block));
        return new Loaded(report, data.exercises, week.phaseId(), until(sessions, to, zone));
    }

    @Transactional(readOnly = true)
    public Loaded phase(UUID userId, UUID phaseId) {
        PhaseDetails phase = trainingReader.phaseDetails(userId, phaseId);
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(userId);
        List<WorkoutSession> sessions = sessionRepository.findByUserIdAndPhaseIdOrderByStartedAtAsc(userId, phaseId);
        Data data = load(userId, sessions, zone);
        List<DayRef> days = trainingReader.days(userId, phaseId);

        LocalDate from = phase.phase().startDate() != null ? phase.phase().startDate() : firstDate(sessions, zone);
        LocalDate lastSession = lastDate(sessions, zone);
        LocalDate to = phase.phase().endDate() != null
                ? max(min(phase.phase().endDate(), today), lastSession)
                : lastSession;
        List<TrendPoint> assessments = from == null ? List.of() : assessments(userId, from, to);

        Map<UUID, List<WorkoutSession>> byWeek = new HashMap<>();
        List<WorkoutSession> withoutWeek = new ArrayList<>();
        for (WorkoutSession session : sessions) {
            if (session.getWeekId() == null) {
                withoutWeek.add(session);
            } else {
                byWeek.computeIfAbsent(session.getWeekId(), id -> new ArrayList<>()).add(session);
            }
        }
        List<Report.Week> weeks = new ArrayList<>();
        for (WeekRef week : trainingReader.weeks(userId, phaseId)) {
            List<WorkoutSession> weekSessions = byWeek.getOrDefault(week.id(), List.of());
            boolean started = week.startDate() != null && !week.startDate().isAfter(today);
            if (weekSessions.isEmpty() && !started) {
                continue;
            }
            weeks.add(week(week.weekNumber(), week.deload(), week.startDate(), weekSessions, days, data, assessments, zone, today));
        }
        if (!withoutWeek.isEmpty()) {
            weeks.add(week(null, false, null, withoutWeek, List.of(), data, List.of(), zone, today));
        }
        Report report = new Report(ReportScope.PHASE, phase.planName(), phase.phase().name(), from, to, weeks);
        return new Loaded(report, data.exercises, phaseId, until(sessions, to, zone));
    }

    // ------------------------------------------------------------------ montagem

    /**
     * Semana: os dias planejados em ordem (com a sessão feita, ou "não realizado" se o dia já passou)
     * e depois as sessões fora do plano da fase, em ordem cronológica.
     */
    private Report.Week week(
            Integer number,
            boolean deload,
            LocalDate start,
            List<WorkoutSession> sessions,
            List<DayRef> plannedDays,
            Data data,
            List<TrendPoint> assessments,
            ZoneId zone,
            LocalDate today) {
        LocalDate end = start == null ? null : start.plusDays(6);
        List<Report.Day> days = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (DayRef planned : plannedDays) {
            List<WorkoutSession> done = sessions.stream()
                    .filter(session -> planned.id().equals(session.getWorkoutDayId()))
                    .toList();
            for (WorkoutSession session : done) {
                used.add(session.getId());
                days.add(new Report.Day(
                        date(session, zone).getDayOfWeek(), planned.name(), planned.id(), data.sessions.get(session.getId())));
            }
            if (done.isEmpty() && isPast(planned.dayOfWeek(), start, today)) {
                days.add(new Report.Day(planned.dayOfWeek(), planned.name(), planned.id(), null));
            }
        }
        for (WorkoutSession session : sessions) {
            if (!used.contains(session.getId())) {
                days.add(new Report.Day(
                        date(session, zone).getDayOfWeek(), dayName(session), session.getWorkoutDayId(),
                        data.sessions.get(session.getId())));
            }
        }
        BigDecimal weight = start == null ? null : latestWeight(assessments, start, end);
        return new Report.Week(number, deload, start, end, weight, days);
    }

    /** O dia planejado já passou sem sessão? Sem dia da semana definido, só quando a semana acabou. */
    private static boolean isPast(DayOfWeek dayOfWeek, LocalDate weekStart, LocalDate today) {
        if (weekStart == null) {
            return false;
        }
        LocalDate limit = dayOfWeek == null
                ? weekStart.plusDays(6)
                : weekStart.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        return limit.isBefore(today);
    }

    /** Exercícios, séries e observações de todas as sessões, em poucas consultas. */
    private Data load(UUID userId, List<WorkoutSession> sessions, ZoneId zone) {
        Data data = new Data();
        if (sessions.isEmpty()) {
            return data;
        }
        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();
        List<SessionExercise> exercises = sessionExerciseRepository.findBySessions(sessionIds, userId);
        data.exercises.addAll(exercises);
        Map<UUID, List<ExerciseSet>> sets = exercises.isEmpty()
                ? Map.of()
                : setRepository.findBySessionExercises(exercises.stream().map(SessionExercise::getId).toList(), userId)
                        .stream()
                        .collect(Collectors.groupingBy(set -> set.getSessionExercise().getId()));
        Map<UUID, List<WorkoutObservation>> observations = observationRepository
                .findByUserIdAndSessionIdInOrderByObservedAtAsc(userId, sessionIds).stream()
                .collect(Collectors.groupingBy(WorkoutObservation::getSessionId));

        Map<UUID, List<SessionExercise>> bySession = exercises.stream()
                .collect(Collectors.groupingBy(e -> e.getSession().getId(), LinkedHashMap::new, Collectors.toList()));
        for (WorkoutSession session : sessions) {
            List<SessionExercise> items = bySession.getOrDefault(session.getId(), List.of());
            data.sessions.put(session.getId(), new Report.Session(
                    session.getId(),
                    date(session, zone),
                    session.getStartedAt(),
                    session.isInProgress(),
                    session.getNotes(),
                    exercises(items, sets),
                    observations(observations.getOrDefault(session.getId(), List.of()), items)));
        }
        return data;
    }

    /**
     * Exercícios em ordem. O substituído não aparece sozinho: o substituto leva o nome dele em
     * {@code replaces}.
     */
    private static List<Report.Exercise> exercises(List<SessionExercise> items, Map<UUID, List<ExerciseSet>> sets) {
        Map<UUID, String> replaced = new HashMap<>();
        for (SessionExercise item : items) {
            if (item.getStatus() == SessionExerciseStatus.SUBSTITUTED && item.getSubstitutedById() != null) {
                replaced.put(item.getSubstitutedById(), item.getExerciseName());
            }
        }
        List<Report.Exercise> result = new ArrayList<>();
        for (SessionExercise item : items) {
            if (item.getStatus() == SessionExerciseStatus.SUBSTITUTED) {
                continue;
            }
            result.add(new Report.Exercise(
                    item.getId(),
                    item.getExercise().getId(),
                    item.getExerciseName(),
                    item.getMuscleGroup(),
                    replaced.get(item.getId()),
                    item.getStatus(),
                    item.getPlannedSets(),
                    item.getRepsMin(),
                    item.getRepsMax(),
                    item.getTargetRir(),
                    item.getNotes(),
                    sets(sets.getOrDefault(item.getId(), List.of()))));
        }
        return result;
    }

    /** Drop set e rest-pause entram dentro da série principal (A13); sem série principal, ficam soltos. */
    private static List<Report.Set> sets(List<ExerciseSet> sets) {
        Map<UUID, List<ExerciseSet>> extensions = new HashMap<>();
        Set<UUID> ids = sets.stream().map(ExerciseSet::getId).collect(Collectors.toSet());
        for (ExerciseSet set : sets) {
            if (isExtension(set) && set.getParentSetId() != null && ids.contains(set.getParentSetId())) {
                extensions.computeIfAbsent(set.getParentSetId(), id -> new ArrayList<>()).add(set);
            }
        }
        return sets.stream()
                .filter(set -> !(isExtension(set) && set.getParentSetId() != null && ids.contains(set.getParentSetId())))
                .sorted(Comparator.comparingInt(ExerciseSet::getSetNumber))
                .map(set -> toSet(set, extensions.getOrDefault(set.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(ExerciseSet::getSetNumber))
                        .map(extension -> toSet(extension, List.of()))
                        .toList()))
                .toList();
    }

    private static boolean isExtension(ExerciseSet set) {
        return set.getSetType() == SetType.DROP_SET || set.getSetType() == SetType.REST_PAUSE;
    }

    private static Report.Set toSet(ExerciseSet set, List<Report.Set> extensions) {
        return new Report.Set(
                set.getSetNumber(), set.getSetType(), set.getStatus(), set.getLoadKg(), set.getReps(), set.getRir(),
                set.getNotes(), extensions);
    }

    private static List<Report.Observation> observations(List<WorkoutObservation> observations, List<SessionExercise> items) {
        Map<UUID, String> bySessionExercise = new HashMap<>();
        Map<UUID, String> byExercise = new HashMap<>();
        for (SessionExercise item : items) {
            bySessionExercise.put(item.getId(), item.getExerciseName());
            byExercise.putIfAbsent(item.getExercise().getId(), item.getExerciseName());
        }
        return observations.stream()
                .map(o -> new Report.Observation(
                        o.getCategory(),
                        o.getBodyRegion(),
                        o.getBodySide(),
                        o.getIntensity(),
                        o.isInterrupted(),
                        o.getSessionExerciseId() != null
                                ? bySessionExercise.get(o.getSessionExerciseId())
                                : o.getExerciseId() == null ? null : byExercise.get(o.getExerciseId()),
                        o.getText(),
                        o.getObservedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ apoio

    private List<TrendPoint> assessments(UUID userId, LocalDate from, LocalDate to) {
        return assessmentService.between(userId, from, to);
    }

    /** Peso da avaliação mais recente dentro do intervalo; sem avaliação com peso, nulo (A6). */
    static BigDecimal latestWeight(List<TrendPoint> assessments, LocalDate from, LocalDate to) {
        BigDecimal weight = null;
        for (TrendPoint point : assessments) {
            if (point.weightKg() != null && !point.assessedOn().isBefore(from) && !point.assessedOn().isAfter(to)) {
                weight = point.weightKg();
            }
        }
        return weight;
    }

    private static String dayName(WorkoutSession session) {
        return session.getDayName() != null ? session.getDayName() : "Treino avulso";
    }

    private static LocalDate date(WorkoutSession session, ZoneId zone) {
        return session.getStartedAt().atZone(zone).toLocalDate();
    }

    private static LocalDate firstDate(List<WorkoutSession> sessions, ZoneId zone) {
        return sessions.isEmpty() ? null : date(sessions.get(0), zone);
    }

    private static LocalDate lastDate(List<WorkoutSession> sessions, ZoneId zone) {
        return sessions.isEmpty() ? null : date(sessions.get(sessions.size() - 1), zone);
    }

    private static Instant until(WorkoutSession session) {
        return session.getFinishedAt() != null ? session.getFinishedAt() : Instant.now();
    }

    /** Fim do período para o histórico da análise: a última sessão ou o fim do último dia. */
    private static Instant until(List<WorkoutSession> sessions, LocalDate to, ZoneId zone) {
        Instant last = sessions.stream().map(ReportLoader::until).max(Comparator.naturalOrder()).orElse(null);
        Instant endOfPeriod = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        if (last == null) {
            return endOfPeriod == null ? Instant.now() : endOfPeriod;
        }
        return endOfPeriod == null || last.isAfter(endOfPeriod) ? last : endOfPeriod;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static final class Data {
        private final Map<UUID, Report.Session> sessions = new HashMap<>();
        private final List<SessionExercise> exercises = new ArrayList<>();
    }
}
