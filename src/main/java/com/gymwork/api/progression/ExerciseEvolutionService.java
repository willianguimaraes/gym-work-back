package com.gymwork.api.progression;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.exercise.dto.ExerciseResponse;
import com.gymwork.api.progression.ExecutionRecord.SetRecord;
import com.gymwork.api.progression.dto.ExerciseHistoryItem;
import com.gymwork.api.progression.dto.ExerciseSummaryResponse;
import com.gymwork.api.progression.dto.ExerciseSummaryResponse.BestSet;
import com.gymwork.api.progression.dto.ProgressPoint;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.SessionExercise;
import com.gymwork.api.workout.SessionExerciseRepository;
import com.gymwork.api.workout.WorkoutObservationRepository;
import com.gymwork.api.workout.dto.ObservationResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evolução de um exercício: histórico por sessão, resumo (última, melhor, análise) e dados do
 * gráfico. Só sessões finalizadas entram. Cada exercício do catálogo tem um equipamento, então nunca
 * se compara carga de equipamentos diferentes.
 */
@Service
public class ExerciseEvolutionService {

    public enum GroupBy {
        SESSION,
        WEEK,
        PHASE
    }

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final SessionExerciseRepository sessionExerciseRepository;
    private final WorkoutObservationRepository observationRepository;
    private final ExerciseService exerciseService;
    private final ProgressionRuleService ruleService;
    private final ExecutionLoader loader;
    private final UserTime userTime;

    public ExerciseEvolutionService(
            SessionExerciseRepository sessionExerciseRepository,
            WorkoutObservationRepository observationRepository,
            ExerciseService exerciseService,
            ProgressionRuleService ruleService,
            ExecutionLoader loader,
            UserTime userTime) {
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.observationRepository = observationRepository;
        this.exerciseService = exerciseService;
        this.ruleService = ruleService;
        this.loader = loader;
        this.userTime = userTime;
    }

    /** Execuções do exercício, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public PageResponse<ExerciseHistoryItem> history(UUID userId, UUID exerciseId, Pageable pageable) {
        exerciseService.get(userId, exerciseId);
        Page<SessionExercise> page = sessionExerciseRepository.findCompletedByExercise(userId, exerciseId, pageable);
        List<ExerciseHistoryItem> items = loader.records(userId, page.getContent()).stream()
                .map(Views::historyItem)
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExerciseSummaryResponse summary(UUID userId, UUID exerciseId) {
        ExerciseResponse exercise = exerciseService.get(userId, exerciseId);
        List<ExecutionRecord> records = records(userId, exerciseId);
        RuleSettings rule = ruleService.settings(userId, exerciseId);
        ProgressionAnalyzer.Analysis analysis = ProgressionAnalyzer.analyze(records, rule);

        ExecutionRecord last = records.isEmpty() ? null : records.get(records.size() - 1);
        BestSet best = null;
        BigDecimal bestE1rm = null;
        java.time.Instant bestE1rmAt = null;
        for (ExecutionRecord record : records) {
            for (SetRecord set : record.validSets()) {
                if (set.reps() == null) {
                    continue;
                }
                BigDecimal load = set.loadKg() == null ? BigDecimal.ZERO : set.loadKg();
                if (best == null
                        || load.compareTo(best.loadKg()) > 0
                        || (load.compareTo(best.loadKg()) == 0 && set.reps() > best.reps())) {
                    best = new BestSet(load, set.reps(), record.performedAt());
                }
            }
            BigDecimal e1rm = record.bestEstimatedOneRepMax();
            if (e1rm != null && (bestE1rm == null || e1rm.compareTo(bestE1rm) > 0)) {
                bestE1rm = e1rm;
                bestE1rmAt = record.performedAt();
            }
        }
        List<ObservationResponse> observations = observationRepository
                .findTop20ByUserIdAndExerciseIdOrderByObservedAtDesc(userId, exerciseId).stream()
                .map(ObservationResponse::from)
                .toList();

        return new ExerciseSummaryResponse(
                exercise.id(),
                exercise.name(),
                exercise.equipment(),
                records.size(),
                last == null ? null : last.performedAt(),
                last == null ? null : last.summary(),
                last == null ? null : last.topLoad(),
                last == null ? List.of() : last.validReps(),
                best,
                bestE1rm,
                bestE1rmAt,
                Views.analysis(analysis),
                ruleService.get(userId, exerciseId),
                observations);
    }

    /** Pontos do gráfico de carga e repetições, em ordem cronológica. */
    @Transactional(readOnly = true)
    public List<ProgressPoint> progress(UUID userId, UUID exerciseId, GroupBy groupBy) {
        exerciseService.get(userId, exerciseId);
        ZoneId zone = userTime.zone(userId);
        Map<String, List<ExecutionRecord>> groups = new LinkedHashMap<>();
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        for (ExecutionRecord record : records(userId, exerciseId)) {
            if (record.validSets().isEmpty()) {
                continue;
            }
            LocalDate date = record.performedAt().atZone(zone).toLocalDate();
            String key;
            LocalDate groupDate;
            switch (groupBy) {
                case WEEK -> {
                    groupDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    key = "Sem. " + groupDate.format(DAY_MONTH);
                }
                case PHASE -> {
                    key = record.phaseName() == null ? "Sem fase" : record.phaseName();
                    groupDate = date;
                }
                default -> {
                    key = record.sessionId().toString();
                    groupDate = date;
                }
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            dates.putIfAbsent(key, groupDate);
        }
        List<ProgressPoint> points = new ArrayList<>();
        groups.forEach((key, group) -> {
            LocalDate date = dates.get(key);
            String label = groupBy == GroupBy.SESSION ? date.format(DAY_MONTH) : key;
            points.add(point(label, date, group));
        });
        return points;
    }

    private static ProgressPoint point(String label, LocalDate date, List<ExecutionRecord> group) {
        BigDecimal top = group.stream().map(ExecutionRecord::topLoad).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        List<Integer> repsAtTop = group.stream()
                .flatMap(record -> record.validSets().stream())
                .filter(set -> set.load().compareTo(top) == 0 && set.reps() != null)
                .map(SetRecord::reps)
                .toList();
        BigDecimal avgReps = repsAtTop.isEmpty() ? null : BigDecimal.valueOf(
                repsAtTop.stream().mapToInt(Integer::intValue).average().orElse(0)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal e1rm = group.stream().map(ExecutionRecord::bestEstimatedOneRepMax).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new ProgressPoint(
                label,
                date,
                group.size(),
                top,
                avgReps,
                group.stream().mapToInt(record -> record.validSets().size()).sum(),
                group.stream().mapToInt(ExecutionRecord::totalReps).sum(),
                group.stream().map(ExecutionRecord::volumeKg).reduce(BigDecimal.ZERO, BigDecimal::add),
                e1rm);
    }

    private List<ExecutionRecord> records(UUID userId, UUID exerciseId) {
        return loader.records(userId, sessionExerciseRepository.findAllCompletedByExercise(userId, exerciseId));
    }
}
