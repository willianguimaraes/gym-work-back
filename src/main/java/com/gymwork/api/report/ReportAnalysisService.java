package com.gymwork.api.report;

import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.exercise.dto.MuscleGroupResponse;
import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import com.gymwork.api.progression.ScopeAnalysisService;
import com.gymwork.api.progression.ScopeAnalysisService.ExerciseChange;
import com.gymwork.api.report.ReportLoader.Loaded;
import com.gymwork.api.report.dto.AnalysisReport;
import com.gymwork.api.user.WeightUnit;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumo de análise de um relatório já carregado (sessão, semana ou fase). */
@Service
public class ReportAnalysisService {

    private final ScopeAnalysisService scopeAnalysis;
    private final ExerciseService exerciseService;

    private final ReportLoader loader;

    public ReportAnalysisService(ScopeAnalysisService scopeAnalysis, ExerciseService exerciseService, ReportLoader loader) {
        this.scopeAnalysis = scopeAnalysis;
        this.exerciseService = exerciseService;
        this.loader = loader;
    }

    @Transactional(readOnly = true)
    public AnalysisReport session(UUID userId, UUID sessionId) {
        return analyze(userId, loader.session(userId, sessionId));
    }

    @Transactional(readOnly = true)
    public AnalysisReport week(UUID userId, UUID weekId) {
        return analyze(userId, loader.week(userId, weekId));
    }

    @Transactional(readOnly = true)
    public AnalysisReport phase(UUID userId, UUID phaseId) {
        return analyze(userId, loader.phase(userId, phaseId));
    }

    /** Precisa rodar na mesma transação em que o relatório foi carregado (entidades da sessão). */
    @Transactional(readOnly = true)
    public AnalysisReport analyze(UUID userId, Loaded loaded) {
        Report report = loaded.report();
        ScopeAnalysisService.Result result = scopeAnalysis.analyze(
                userId, loaded.exercises(), loaded.phaseId(), loaded.until());

        List<ExerciseChange> progressed = new ArrayList<>();
        List<ExerciseChange> maintained = new ArrayList<>();
        List<ExerciseChange> regressed = new ArrayList<>();
        List<ExerciseChange> inconclusive = new ArrayList<>();
        List<String> loadChanges = new ArrayList<>();
        List<String> repChanges = new ArrayList<>();
        for (ExerciseChange change : result.exercises()) {
            Trend trend = change.analysis().trend();
            switch (trend) {
                case PROGRESSING -> progressed.add(change);
                case STABLE -> maintained.add(change);
                case REGRESSING -> regressed.add(change);
                default -> inconclusive.add(change);
            }
            if (change.loadChanged()) {
                loadChanges.add(change.exerciseName() + ": " + ReportLabels.load(change.loadFromKg(), WeightUnit.KG)
                        + " → " + ReportLabels.load(change.loadToKg(), WeightUnit.KG));
            } else if (change.repsChanged()) {
                String load = change.loadToKg() == null || change.loadToKg().signum() == 0
                        ? ""
                        : " (" + ReportLabels.load(change.loadToKg(), WeightUnit.KG) + ")";
                repChanges.add(change.exerciseName() + load + ": " + reps(change.repsFrom()) + " → " + reps(change.repsTo()));
            }
        }

        Map<String, String> muscleNames = exerciseService.muscleGroups().stream()
                .collect(Collectors.toMap(MuscleGroupResponse::code, MuscleGroupResponse::name));
        List<AnalysisReport.MuscleVolume> volume = result.volume().stream()
                .map(v -> new AnalysisReport.MuscleVolume(
                        v.muscleGroup(), muscleNames.getOrDefault(v.muscleGroup(), v.muscleGroup()), v.sets(), v.secondarySets()))
                .toList();

        List<AnalysisReport.Missed> missed = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        List<String> discomforts = new ArrayList<>();
        int completed = 0;
        int inProgress = 0;
        int missedDays = 0;
        int numberedWeeks = 0;
        int plannedDays = 0;
        for (Report.Week week : report.weeks()) {
            if (week.number() != null) {
                numberedWeeks++;
            }
            plannedDays = Math.max(plannedDays, (int) week.days().stream()
                    .map(Report.Day::workoutDayId).filter(Objects::nonNull).distinct().count());
            for (Report.Day day : week.days()) {
                String when = when(report, week, day);
                Report.Session session = day.session();
                if (session == null) {
                    missedDays++;
                    missed.add(new AnalysisReport.Missed(when, null, "dia não realizado"));
                    continue;
                }
                if (session.inProgress()) {
                    inProgress++;
                } else {
                    completed++;
                }
                if (session.notes() != null && !session.notes().isBlank()) {
                    observations.add(when + ": " + session.notes().strip().replace('\n', ' '));
                }
                for (Report.Exercise exercise : session.exercises()) {
                    String detail = missedDetail(exercise, session.inProgress());
                    if (detail != null) {
                        missed.add(new AnalysisReport.Missed(when, exercise.name(), detail));
                    }
                    if (exercise.notes() != null && !exercise.notes().isBlank()) {
                        observations.add(when + " · " + exercise.name() + ": " + exercise.notes().strip().replace('\n', ' '));
                    }
                }
                for (Report.Observation observation : session.observations()) {
                    String line = when + ": " + ReportLabels.observation(observation);
                    if (observation.category() == ObservationCategory.PAIN
                            || observation.category() == ObservationCategory.DISCOMFORT) {
                        discomforts.add(line);
                    } else {
                        observations.add(line);
                    }
                }
            }
        }
        int weeks = report.scope() == ReportScope.SESSION ? 0 : Math.max(numberedWeeks, report.weeks().isEmpty() ? 0 : 1);
        Integer plannedSessions = report.scope() == ReportScope.SESSION || numberedWeeks == 0
                ? null
                : plannedDays * numberedWeeks;
        BigDecimal perWeek = weeks == 0
                ? null
                : BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(weeks), 1, RoundingMode.HALF_UP);
        AnalysisReport.Frequency frequency = new AnalysisReport.Frequency(
                completed, inProgress, plannedSessions, missedDays, weeks, perWeek);

        return new AnalysisReport(
                report.scope(),
                title(report),
                report.planName(),
                report.phaseName(),
                report.from(),
                report.to(),
                frequency,
                progressed,
                maintained,
                regressed,
                inconclusive,
                loadChanges,
                repChanges,
                missed,
                observations,
                discomforts,
                volume,
                result.validSets(),
                result.volumeKg());
    }

    /** "Fase 4 — Controle · Semana 1", "Treino avulso · 26/11/2026"... */
    static String title(Report report) {
        String base = report.phaseName() == null ? "Treino avulso" : report.phaseName();
        if (report.scope() == ReportScope.WEEK && !report.weeks().isEmpty() && report.weeks().get(0).number() != null) {
            return base + " · Semana " + report.weeks().get(0).number();
        }
        if (report.scope() == ReportScope.SESSION) {
            return base + " · " + ReportLabels.date(report.from());
        }
        return base;
    }

    private static String when(Report report, Report.Week week, Report.Day day) {
        String weekday = ReportLabels.weekday(day.weekday());
        String dayText = (weekday == null ? "" : weekday + " — ") + day.name();
        if (report.scope() != ReportScope.SESSION && week.number() != null) {
            return "Semana " + week.number() + " · " + dayText;
        }
        if (day.session() != null) {
            return ReportLabels.date(day.session().date()) + " · " + dayText;
        }
        return dayText;
    }

    /** Exercício pulado, sem séries ou com menos séries que o planejado (sessão finalizada). */
    private static String missedDetail(Report.Exercise exercise, boolean inProgress) {
        if (exercise.status() == SessionExerciseStatus.SKIPPED) {
            return "pulado";
        }
        if (inProgress) {
            return null;
        }
        long done = exercise.sets().stream()
                .filter(set -> set.type().countsAsSet() && set.status() == SetStatus.COMPLETED)
                .count();
        if (done == 0) {
            return "nenhuma série feita";
        }
        if (exercise.plannedSets() != null && done < exercise.plannedSets()) {
            return done + " de " + exercise.plannedSets() + " séries";
        }
        return null;
    }

    private static String reps(List<Integer> reps) {
        return reps.isEmpty() ? "—" : reps.stream().map(String::valueOf).collect(Collectors.joining("/"));
    }
}
