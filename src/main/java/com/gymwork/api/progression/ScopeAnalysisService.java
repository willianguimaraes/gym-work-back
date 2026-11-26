package com.gymwork.api.progression;

import com.gymwork.api.progression.ProgressionAnalyzer.Analysis;
import com.gymwork.api.progression.dto.AnalysisResponse;
import com.gymwork.api.workout.SessionExercise;
import com.gymwork.api.workout.SessionExerciseRepository;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SessionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Análise de um período (sessão, semana ou fase) para os relatórios: tendência de cada exercício
 * feito no período, mudanças de carga e repetições, e volume por grupo muscular. Usa as mesmas
 * regras da tela de evolução; o histórico considerado é o da fase (ou das últimas 26 semanas, em
 * treino avulso) até o fim do período.
 */
@Service
public class ScopeAnalysisService {

    private static final Duration LOOSE_HISTORY = Duration.ofDays(182);

    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExecutionLoader loader;
    private final ProgressionRuleService ruleService;

    ScopeAnalysisService(
            SessionExerciseRepository sessionExerciseRepository,
            ExecutionLoader loader,
            ProgressionRuleService ruleService) {
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.loader = loader;
        this.ruleService = ruleService;
    }

    /**
     * Um exercício no período. {@code baseline}: primeira execução do período (ou a última antes
     * dele, se o período tiver só uma); {@code latest}: a última do período. Cargas e repetições são
     * as da maior carga das séries válidas.
     */
    public record ExerciseChange(
            UUID exerciseId,
            String exerciseName,
            String muscleGroup,
            int executionsInPeriod,
            AnalysisResponse analysis,
            String baselineSummary,
            String latestSummary,
            BigDecimal loadFromKg,
            BigDecimal loadToKg,
            List<Integer> repsFrom,
            List<Integer> repsTo,
            boolean loadChanged,
            boolean repsChanged) {
    }

    /** Séries válidas por grupo muscular: principal e, à parte, como secundário. */
    public record MuscleSets(String muscleGroup, int sets, int secondarySets) {
    }

    public record Result(List<ExerciseChange> exercises, List<MuscleSets> volume, int validSets, BigDecimal volumeKg) {
    }

    /**
     * @param scope exercícios executados no período (as sessões em andamento são ignoradas)
     * @param phaseId fase de referência do histórico; nula em treino avulso
     */
    @Transactional(readOnly = true)
    public Result analyze(UUID userId, List<SessionExercise> scope, UUID phaseId, Instant until) {
        List<SessionExercise> done = scope.stream()
                .filter(e -> e.getSession().getStatus() == SessionStatus.COMPLETED)
                .filter(e -> e.getStatus() != SessionExerciseStatus.SUBSTITUTED)
                .toList();
        if (done.isEmpty()) {
            return new Result(List.of(), List.of(), 0, BigDecimal.ZERO);
        }
        Set<UUID> scopeIds = done.stream().map(SessionExercise::getId).collect(Collectors.toSet());
        List<SessionExercise> history = (phaseId != null
                ? sessionExerciseRepository.findCompletedInPhase(userId, phaseId)
                : sessionExerciseRepository.findCompletedBetween(userId, until.minus(LOOSE_HISTORY), until.plusMillis(1)))
                .stream()
                .filter(e -> !e.getSession().getStartedAt().isAfter(until))
                .toList();
        // garante que o período entra mesmo se estiver fora do recorte do histórico
        List<SessionExercise> all = new ArrayList<>(history);
        Set<UUID> historyIds = history.stream().map(SessionExercise::getId).collect(Collectors.toSet());
        done.stream().filter(e -> !historyIds.contains(e.getId())).forEach(all::add);
        all.sort(Comparator.comparing((SessionExercise e) -> e.getSession().getStartedAt())
                .thenComparingInt(SessionExercise::getPosition));

        List<ExecutionRecord> records = loader.records(userId, all);
        Map<UUID, List<ExecutionRecord>> byExercise = records.stream()
                .collect(Collectors.groupingBy(ExecutionRecord::exerciseId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, SessionExercise> scopeById = done.stream()
                .collect(Collectors.toMap(SessionExercise::getId, e -> e, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, RuleSettings> rules = ruleService.settings(
                userId, done.stream().map(e -> e.getExercise().getId()).collect(Collectors.toSet()));

        List<ExerciseChange> changes = new ArrayList<>();
        Map<UUID, Boolean> seen = new LinkedHashMap<>();
        for (SessionExercise exercise : done) {
            UUID exerciseId = exercise.getExercise().getId();
            if (seen.putIfAbsent(exerciseId, true) != null) {
                continue;
            }
            List<ExecutionRecord> exerciseHistory = byExercise.getOrDefault(exerciseId, List.of());
            Analysis analysis = ProgressionAnalyzer.analyze(
                    exerciseHistory, rules.getOrDefault(exerciseId, RuleSettings.DEFAULT));
            changes.add(change(exercise, exerciseHistory, scopeIds, analysis));
        }

        Map<String, int[]> muscles = new LinkedHashMap<>();
        int validSets = 0;
        BigDecimal volumeKg = BigDecimal.ZERO;
        for (ExecutionRecord record : records) {
            SessionExercise exercise = scopeById.get(record.sessionExerciseId());
            if (exercise == null) {
                continue;
            }
            int sets = record.validSets().size();
            validSets += sets;
            volumeKg = volumeKg.add(record.volumeKg());
            if (sets == 0) {
                continue;
            }
            muscles.computeIfAbsent(exercise.getMuscleGroup(), k -> new int[2])[0] += sets;
            for (String secondary : exercise.getExercise().getSecondaryMuscleGroups()) {
                if (!secondary.equals(exercise.getMuscleGroup())) {
                    muscles.computeIfAbsent(secondary, k -> new int[2])[1] += sets;
                }
            }
        }
        List<MuscleSets> volume = muscles.entrySet().stream()
                .map(entry -> new MuscleSets(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingInt(MuscleSets::sets).reversed()
                        .thenComparing(Comparator.comparingInt(MuscleSets::secondarySets).reversed()))
                .toList();
        return new Result(changes, volume, validSets, volumeKg);
    }

    private static ExerciseChange change(
            SessionExercise exercise, List<ExecutionRecord> history, Set<UUID> scopeIds, Analysis analysis) {
        List<ExecutionRecord> inScope = history.stream()
                .filter(record -> scopeIds.contains(record.sessionExerciseId()))
                .toList();
        List<ExecutionRecord> withSets = inScope.stream().filter(record -> !record.validSets().isEmpty()).toList();
        ExecutionRecord latest = withSets.isEmpty() ? null : withSets.get(withSets.size() - 1);
        ExecutionRecord baseline = null;
        if (withSets.size() >= 2) {
            baseline = withSets.get(0);
        } else if (latest != null) {
            int index = history.indexOf(latest);
            for (int i = index - 1; i >= 0; i--) {
                if (!scopeIds.contains(history.get(i).sessionExerciseId()) && !history.get(i).validSets().isEmpty()) {
                    baseline = history.get(i);
                    break;
                }
            }
        }
        BigDecimal loadFrom = baseline == null ? null : baseline.topLoad();
        BigDecimal loadTo = latest == null ? null : latest.topLoad();
        List<Integer> repsFrom = baseline == null ? List.of() : baseline.repsAtTopLoad();
        List<Integer> repsTo = latest == null ? List.of() : latest.repsAtTopLoad();
        boolean loadChanged = loadFrom != null && loadTo != null && loadFrom.compareTo(loadTo) != 0;
        boolean repsChanged = !loadChanged && baseline != null && latest != null && !repsFrom.equals(repsTo);
        return new ExerciseChange(
                exercise.getExercise().getId(),
                exercise.getExerciseName(),
                exercise.getMuscleGroup(),
                inScope.size(),
                Views.analysis(analysis),
                baseline == null ? null : baseline.summary(),
                latest == null ? null : latest.summary(),
                loadFrom,
                loadTo,
                repsFrom,
                repsTo,
                loadChanged,
                repsChanged);
    }
}
