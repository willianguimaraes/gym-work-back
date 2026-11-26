package com.gymwork.api.progression;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.progression.ExecutionRecord.SetRecord;
import com.gymwork.api.progression.ProgressionAnalyzer.Analysis;
import com.gymwork.api.progression.ProgressionAnalyzer.Suggestion;
import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Casos de borda da análise de progressão (seção 4.6 do prompt e A12/A13). */
class ProgressionAnalyzerTest {

    private static final UUID EXERCISE = UUID.randomUUID();
    private static int day;

    @Test
    void allValidSetsAtTheTopOfTheRangeSuggestLoadIncrease() {
        Analysis analysis = analyze(
                exec(4, 6, 8, null, sets("45", 7, 7, 6, 6)),
                exec(4, 6, 8, null, sets("45", 8, 8, 8, 8)));

        assertThat(analysis.trend()).isEqualTo(Trend.PROGRESSING);
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.INCREASE_LOAD);
        assertThat(analysis.suggestedLoadKg()).isEqualByComparingTo("47.5");
        assertThat(analysis.message()).contains("Todas as 4 séries", "47,5 kg");
    }

    @Test
    void oneSetAtTheTopIsNotEnough() {
        Analysis analysis = analyze(exec(4, 6, 8, null, sets("45", 8, 7, 7, 6)));

        assertThat(analysis.suggestion()).isEqualTo(Suggestion.ADD_REPS);
        assertThat(analysis.message()).contains("1 de 4 séries chegaram a 8 reps");
    }

    @Test
    void warmupsAreIgnoredByTheAnalysis() {
        List<SetRecord> sets = new ArrayList<>(sets("45", 8, 8, 8));
        sets.add(0, new SetRecord(SetType.WARMUP, SetStatus.COMPLETED, new BigDecimal("20"), 15, null));
        sets.add(1, new SetRecord(SetType.ACTIVATION, SetStatus.COMPLETED, new BigDecimal("30"), 3, null));

        Analysis analysis = analyze(exec(3, 6, 8, null, sets));

        assertThat(analysis.latest().validSets()).hasSize(3);
        assertThat(analysis.latest().topLoad()).isEqualByComparingTo("45");
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.INCREASE_LOAD);
    }

    @Test
    void incompleteWorkoutIsNotARegression() {
        Analysis analysis = analyze(
                exec(4, 6, 8, null, sets("45", 8, 8, 7, 7)),
                exec(4, 6, 8, null, sets("45", 6, 5)));

        assertThat(analysis.trend()).isEqualTo(Trend.INCOMPLETE);
        assertThat(analysis.trendDetail()).contains("2 de 4 séries", "não é tratada como queda");
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.NONE);
    }

    @Test
    void interruptedWorkingSetMakesTheExecutionIncomplete() {
        List<SetRecord> sets = new ArrayList<>(sets("45", 8, 8, 8));
        sets.add(new SetRecord(SetType.WORKING, SetStatus.INTERRUPTED, new BigDecimal("45"), 3, null));

        Analysis analysis = analyze(exec(3, 6, 8, null, sets));

        assertThat(analysis.trend()).isEqualTo(Trend.INCOMPLETE);
    }

    @Test
    void painBlocksTheIncreaseSuggestion() {
        Analysis analysis = analyze(withPain(exec(4, 6, 8, null, sets("45", 8, 8, 8, 8))));

        assertThat(analysis.suggestion()).isEqualTo(Suggestion.HOLD_PAIN);
        assertThat(analysis.suggestedLoadKg()).isNull();
        assertThat(analysis.message()).contains("não avalia lesões");
        assertThat(analysis.painReported()).isTrue();
    }

    @Test
    void rirBelowTheMinimumHoldsTheLoad() {
        Analysis analysis = analyze(exec(3, 6, 8, 2, setsWithRir("45", 8, 1, 8, 2, 8, 2)));

        assertThat(analysis.suggestion()).isEqualTo(Suggestion.REVIEW_RIR);
        assertThat(analysis.message()).contains("RIR abaixo de 2");
    }

    @Test
    void customRuleRequiresConsecutiveSessionsAtTheTopAndUsesItsIncrement() {
        RuleSettings rule = new RuleSettings(null, 2, new BigDecimal("5"), true);
        List<ExecutionRecord> once = List.of(
                exec(3, 6, 8, null, sets("60", 7, 7, 7)),
                exec(3, 6, 8, null, sets("60", 8, 8, 8)));
        assertThat(ProgressionAnalyzer.analyze(once, rule).suggestion()).isEqualTo(Suggestion.CONFIRM_TOP);

        List<ExecutionRecord> twice = List.of(
                exec(3, 6, 8, null, sets("60", 8, 8, 8)),
                exec(3, 6, 8, null, sets("60", 8, 8, 8)));
        Analysis analysis = ProgressionAnalyzer.analyze(twice, rule);
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.INCREASE_LOAD);
        assertThat(analysis.suggestedLoadKg()).isEqualByComparingTo("65");
    }

    @Test
    void disabledRuleShowsTrendWithoutSuggestion() {
        Analysis analysis = ProgressionAnalyzer.analyze(
                List.of(exec(3, 6, 8, null, sets("45", 7, 7, 7)), exec(3, 6, 8, null, sets("45", 8, 8, 8))),
                new RuleSettings(null, 1, null, false));

        assertThat(analysis.trend()).isEqualTo(Trend.PROGRESSING);
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.NONE);
    }

    @Test
    void trendComparesLoadThenRepsAtTheSameLoad() {
        assertThat(analyze(exec(3, 6, 8, null, sets("45", 8, 8, 8)), exec(3, 6, 8, null, sets("47.5", 6, 6, 6)))
                .trend()).isEqualTo(Trend.PROGRESSING);
        assertThat(analyze(exec(3, 6, 8, null, sets("45", 7, 7, 6)), exec(3, 6, 8, null, sets("45", 7, 7, 6)))
                .trend()).isEqualTo(Trend.STABLE);
        Analysis down = analyze(exec(3, 6, 8, null, sets("45", 8, 7, 7)), exec(3, 6, 8, null, sets("45", 7, 6, 6)));
        assertThat(down.trend()).isEqualTo(Trend.REGRESSING);
        assertThat(down.suggestion()).isEqualTo(Suggestion.REVIEW_PERFORMANCE);
        assertThat(analyze(exec(3, 6, 8, null, sets("45", 8, 8, 8)), exec(3, 6, 8, null, sets("40", 8, 8, 8)))
                .trend()).isEqualTo(Trend.REGRESSING);
    }

    @Test
    void deloadWeeksAreLeftOutOfTheComparison() {
        ExecutionRecord deload = deload(exec(3, 6, 8, null, sets("30", 8, 8, 8)));

        Analysis afterDeload = analyze(
                exec(3, 6, 8, null, sets("45", 7, 7, 7)),
                deload,
                exec(3, 6, 8, null, sets("45", 8, 7, 7)));
        assertThat(afterDeload.trend()).isEqualTo(Trend.PROGRESSING);
        assertThat(afterDeload.previous().topLoad()).isEqualByComparingTo("45");

        Analysis onDeload = analyze(exec(3, 6, 8, null, sets("45", 7, 7, 7)), deload);
        assertThat(onDeload.trend()).isNotEqualTo(Trend.REGRESSING);
        assertThat(onDeload.suggestion()).isEqualTo(Suggestion.NONE);
    }

    @Test
    void firstExecutionHasNoTrendYet() {
        Analysis analysis = analyze(exec(3, 6, 8, null, sets("45", 7, 7, 6)));

        assertThat(analysis.trend()).isEqualTo(Trend.INSUFFICIENT_DATA);
        assertThat(analysis.suggestion()).isEqualTo(Suggestion.ADD_REPS);
        assertThat(ProgressionAnalyzer.analyze(List.of(), RuleSettings.DEFAULT).trend())
                .isEqualTo(Trend.INSUFFICIENT_DATA);
    }

    @Test
    void dropSetsCountInVolumeButNotAsSets() {
        List<SetRecord> sets = new ArrayList<>(sets("40", 10));
        sets.add(new SetRecord(SetType.DROP_SET, SetStatus.COMPLETED, new BigDecimal("20"), 8, null));
        sets.add(new SetRecord(SetType.WARMUP, SetStatus.COMPLETED, new BigDecimal("20"), 10, null));
        ExecutionRecord record = exec(1, 8, 12, null, sets);

        assertThat(record.validSets()).hasSize(1);
        assertThat(record.volumeKg()).isEqualByComparingTo("560"); // 40×10 + 20×8, sem o aquecimento
        assertThat(record.summary()).isEqualTo("40 kg — 10");
    }

    @Test
    void summaryGroupsSetsByLoad() {
        List<SetRecord> sets = new ArrayList<>(sets("45", 8, 7));
        sets.addAll(sets("40", 10));
        assertThat(exec(3, 6, 10, null, sets).summary()).isEqualTo("45 kg — 8/7 · 40 kg — 10");
        assertThat(exec(3, 6, 10, null, sets("22.5", 10)).summary()).isEqualTo("22,5 kg — 10");
    }

    // ------------------------------------------------------------------------------------------

    private static Analysis analyze(ExecutionRecord... history) {
        return ProgressionAnalyzer.analyze(List.of(history), RuleSettings.DEFAULT);
    }

    private static List<SetRecord> sets(String load, int... reps) {
        List<SetRecord> sets = new ArrayList<>();
        for (int r : reps) {
            sets.add(new SetRecord(SetType.WORKING, SetStatus.COMPLETED, new BigDecimal(load), r, null));
        }
        return sets;
    }

    /** Pares (reps, rir). */
    private static List<SetRecord> setsWithRir(String load, int... repsAndRir) {
        List<SetRecord> sets = new ArrayList<>();
        for (int i = 0; i < repsAndRir.length; i += 2) {
            sets.add(new SetRecord(SetType.WORKING, SetStatus.COMPLETED, new BigDecimal(load), repsAndRir[i], repsAndRir[i + 1]));
        }
        return sets;
    }

    private static ExecutionRecord exec(int plannedSets, int repsMin, int repsMax, Integer targetRir, List<SetRecord> sets) {
        return new ExecutionRecord(
                UUID.randomUUID(), UUID.randomUUID(), EXERCISE,
                Instant.parse("2026-01-01T10:00:00Z").plusSeconds(86_400L * day++),
                "UPPER", null, "Fase", 1, false, SessionExerciseStatus.DONE,
                plannedSets, repsMin, repsMax, targetRir, sets, false);
    }

    private static ExecutionRecord withPain(ExecutionRecord r) {
        return new ExecutionRecord(r.sessionId(), r.sessionExerciseId(), r.exerciseId(), r.performedAt(), r.dayName(),
                r.phaseId(), r.phaseName(), r.weekNumber(), r.deload(), r.status(), r.plannedSets(), r.repsMin(),
                r.repsMax(), r.targetRir(), r.sets(), true);
    }

    private static ExecutionRecord deload(ExecutionRecord r) {
        return new ExecutionRecord(r.sessionId(), r.sessionExerciseId(), r.exerciseId(), r.performedAt(), r.dayName(),
                r.phaseId(), r.phaseName(), r.weekNumber(), true, r.status(), r.plannedSets(), r.repsMin(),
                r.repsMax(), r.targetRir(), r.sets(), false);
    }
}
