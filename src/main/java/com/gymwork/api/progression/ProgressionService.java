package com.gymwork.api.progression;

import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import com.gymwork.api.progression.dto.ProgressionItem;
import com.gymwork.api.progression.dto.ProgressionReport;
import com.gymwork.api.progression.dto.VolumeReport;
import com.gymwork.api.progression.dto.VolumeReport.MuscleVolume;
import com.gymwork.api.training.TrainingReader;
import com.gymwork.api.training.TrainingReader.PhaseRef;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.SessionExercise;
import com.gymwork.api.workout.SessionExerciseRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visão geral da evolução: análise de progressão de todos os exercícios e volume semanal. */
@Service
public class ProgressionService {

    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExecutionLoader loader;
    private final ProgressionRuleService ruleService;
    private final TrainingReader trainingReader;
    private final UserTime userTime;

    public ProgressionService(
            SessionExerciseRepository sessionExerciseRepository,
            ExecutionLoader loader,
            ProgressionRuleService ruleService,
            TrainingReader trainingReader,
            UserTime userTime) {
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.loader = loader;
        this.ruleService = ruleService;
        this.trainingReader = trainingReader;
        this.userTime = userTime;
    }

    /**
     * Análise por exercício: das sessões de uma fase, ou das últimas {@code weeks} semanas. Ordem:
     * em progresso, estáveis, em queda, incompletos, poucos dados; depois por nome.
     */
    @Transactional(readOnly = true)
    public ProgressionReport report(UUID userId, UUID phaseId, int weeks) {
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(userId);
        List<SessionExercise> exercises;
        String phaseName = null;
        LocalDate from = null;
        LocalDate to = null;
        if (phaseId != null) {
            PhaseRef phase = trainingReader.phase(userId, phaseId);
            phaseName = phase.name();
            exercises = sessionExerciseRepository.findCompletedInPhase(userId, phaseId);
        } else {
            from = today.minusWeeks(weeks).plusDays(1);
            to = today;
            exercises = sessionExerciseRepository.findCompletedBetween(
                    userId, from.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
        }

        Map<UUID, List<SessionExercise>> byExercise = exercises.stream()
                .collect(Collectors.groupingBy(e -> e.getExercise().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, RuleSettings> rules = ruleService.settings(userId, byExercise.keySet());
        Map<UUID, List<ExecutionRecord>> records = loader.records(userId, exercises).stream()
                .collect(Collectors.groupingBy(ExecutionRecord::exerciseId));

        List<ProgressionItem> items = new ArrayList<>();
        byExercise.forEach((exerciseId, executions) -> {
            SessionExercise latest = executions.get(executions.size() - 1);
            List<ExecutionRecord> history = records.getOrDefault(exerciseId, List.of());
            var analysis = ProgressionAnalyzer.analyze(history, rules.getOrDefault(exerciseId, RuleSettings.DEFAULT));
            items.add(new ProgressionItem(
                    exerciseId,
                    latest.getExerciseName(),
                    latest.getEquipment(),
                    latest.getMuscleGroup(),
                    history.size(),
                    latest.getSession().getStartedAt(),
                    Views.analysis(analysis)));
        });
        items.sort(Comparator.comparing((ProgressionItem item) -> item.analysis().trend().ordinal())
                .thenComparing(ProgressionItem::exerciseName, String.CASE_INSENSITIVE_ORDER));

        Map<Trend, Long> counts = new EnumMap<>(Trend.class);
        for (Trend trend : Trend.values()) {
            counts.put(trend, items.stream().filter(item -> item.analysis().trend() == trend).count());
        }
        return new ProgressionReport(phaseId, phaseName, from, to, counts, items);
    }

    /**
     * Volume semanal das últimas {@code weeks} semanas (segunda a domingo, fuso do usuário): sessões,
     * séries válidas, volume de carga e séries por grupo muscular (principal e secundários à parte).
     */
    @Transactional(readOnly = true)
    public VolumeReport volume(UUID userId, int weeks) {
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(userId);
        LocalDate firstWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1L);
        Instant from = firstWeek.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<SessionExercise> exercises = sessionExerciseRepository.findCompletedBetween(userId, from, to);
        Map<UUID, SessionExercise> byId = exercises.stream()
                .collect(Collectors.toMap(SessionExercise::getId, Function.identity()));

        Map<LocalDate, WeekAccumulator> byWeek = new TreeMap<>();
        for (int i = 0; i < weeks; i++) {
            byWeek.put(firstWeek.plusWeeks(i), new WeekAccumulator());
        }
        WeekAccumulator total = new WeekAccumulator();
        for (ExecutionRecord record : loader.records(userId, exercises)) {
            LocalDate week = record.performedAt().atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            WeekAccumulator accumulator = byWeek.get(week);
            if (accumulator == null) {
                continue;
            }
            SessionExercise exercise = byId.get(record.sessionExerciseId());
            Set<String> secondaries = exercise.getExercise().getSecondaryMuscleGroups();
            accumulator.add(record, exercise.getMuscleGroup(), secondaries);
            total.add(record, exercise.getMuscleGroup(), secondaries);
        }
        List<VolumeReport.Week> result = new ArrayList<>();
        byWeek.forEach((week, accumulator) -> result.add(new VolumeReport.Week(
                week, accumulator.sessions.size(), accumulator.validSets, accumulator.volumeKg, accumulator.muscles())));
        return new VolumeReport(firstWeek, today, result, total.muscles());
    }

    /** Soma de uma semana. Séries válidas = trabalho e adicionais concluídas (VolumeCalculator). */
    private static final class WeekAccumulator {

        private final Set<UUID> sessions = new HashSet<>();
        private final Map<String, int[]> muscles = new LinkedHashMap<>();
        private int validSets;
        private BigDecimal volumeKg = BigDecimal.ZERO;

        void add(ExecutionRecord record, String primary, Set<String> secondaries) {
            sessions.add(record.sessionId());
            int sets = record.validSets().size();
            validSets += sets;
            volumeKg = volumeKg.add(record.volumeKg());
            if (sets == 0) {
                return;
            }
            muscles.computeIfAbsent(primary, k -> new int[2])[0] += sets;
            for (String secondary : secondaries) {
                if (!secondary.equals(primary)) {
                    muscles.computeIfAbsent(secondary, k -> new int[2])[1] += sets;
                }
            }
        }

        List<MuscleVolume> muscles() {
            return muscles.entrySet().stream()
                    .map(entry -> new MuscleVolume(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                    .sorted(Comparator.comparingInt(MuscleVolume::sets).reversed()
                            .thenComparing(Comparator.comparingInt(MuscleVolume::secondarySets).reversed()))
                    .toList();
        }
    }
}
