package com.gymwork.api.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.user.WeightUnit;
import com.gymwork.api.workout.BodyRegion;
import com.gymwork.api.workout.BodySide;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportTextTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    @Test
    void weekReportFollowsTheTrackingFormat() {
        Report.Session upper = session(MONDAY, false, "Treino concluído normalmente.",
                List.of(
                        exercise("Supino inclinado máquina", 4, working("45", 8), working("45", 8), working("45", 7), working("45", 6)),
                        exercise("Elevação lateral", 3, working("10", 15), working("10", 14), working("10", 12))),
                List.of());
        Report.Session lower = session(MONDAY.plusDays(1), false, null,
                List.of(exercise("Cadeira extensora", 3, working("50", 12), working("50", 12), working("50", 12))),
                List.of());
        Report.Week week = new Report.Week(1, false, MONDAY, MONDAY.plusDays(6), new BigDecimal("100.00"), List.of(
                new Report.Day(DayOfWeek.MONDAY, "UPPER", UUID.randomUUID(), upper),
                new Report.Day(DayOfWeek.TUESDAY, "LOWER A", UUID.randomUUID(), lower),
                new Report.Day(DayOfWeek.THURSDAY, "Lower B", UUID.randomUUID(), null)));
        Report report = new Report(ReportScope.WEEK, "Hipertrofia", "Fase 4 — Controle", MONDAY, MONDAY.plusDays(6), List.of(week));

        assertThat(ReportText.text(report, WeightUnit.KG)).isEqualTo("""
                ══════════════════════
                FASE 4 — CONTROLE
                ══════════════════════

                SEMANA 1
                Peso: 100 kg

                SEG — UPPER

                Supino inclinado máquina: 45 kg | 8/8/7/6
                Elevação lateral: 10 kg | 15/14/12

                Observações:
                Treino concluído normalmente.
                ________________________________

                TER — LOWER A

                Cadeira extensora: 50 kg | 12/12/12

                Observações:
                ________________________________

                QUI — LOWER B

                Não realizado
                ________________________________
                """);
    }

    @Test
    void exerciseLineMarksMissingSetsLoadsSubstitutionsAndBodyweight() {
        // cargas diferentes viram grupos
        assertThat(line(exercise("Supino", 4, working("45", 8), working("45", 8), working("40", 7), working("40", 6))))
                .isEqualTo("Supino: 45 kg | 8/8 · 40 kg | 7/6");
        // série não feita registrada e planejada que nem foi registrada
        assertThat(line(exercise("Puxada neutra", 4, working("65", 10), working("65", 10),
                set(SetType.WORKING, SetStatus.NOT_PERFORMED, null, null))))
                .isEqualTo("Puxada neutra: 65 kg | 10/10/x/x");
        // aquecimento não aparece; drop set entra na série principal
        Report.Set drop = set(SetType.DROP_SET, SetStatus.COMPLETED, "30", 4);
        assertThat(line(exercise("Rosca", 2, set(SetType.WARMUP, SetStatus.COMPLETED, "10", 15),
                working("14", 10), withExtension(working("14", 8), drop))))
                .isEqualTo("Rosca: 14 kg | 10/8+4");
        // peso corporal: só repetições
        assertThat(line(exercise("Barra fixa", 3, working(null, 8), working("0", 7), working(null, 6))))
                .isEqualTo("Barra fixa: 8/7/6");
        // decimal com vírgula
        assertThat(line(exercise("Desenvolvimento", 2, working("22.5", 10), working("22.50", 9))))
                .isEqualTo("Desenvolvimento: 22,5 kg | 10/9");
        // pulado e sem séries
        assertThat(line(withStatus(exercise("Remada baixa", 3), SessionExerciseStatus.SKIPPED)))
                .isEqualTo("Remada baixa: não realizado");
        assertThat(line(exercise("Crucifixo", 3))).isEqualTo("Crucifixo: não realizado");
        assertThat(ReportText.exerciseLine(exercise("Crucifixo", 3), false, WeightUnit.KG)).isEqualTo("Crucifixo: pendente");
        // substituição
        Report.Exercise replacement = exercise("Remada máquina", 3, working("60", 10), working("60", 10), working("60", 10));
        assertThat(line(replaces(replacement, "Remada baixa")))
                .isEqualTo("Remada baixa → Remada máquina: 60 kg | 10/10/10");
        // em libras
        assertThat(ReportText.exerciseLine(exercise("Supino", 1, working("45", 8)), true, WeightUnit.LB))
                .isEqualTo("Supino: 99,2 lb | 8");
    }

    @Test
    void observationsIncludeSessionExerciseAndBodyNotes() {
        Report.Exercise bench = new Report.Exercise(UUID.randomUUID(), UUID.randomUUID(), "Supino", "CHEST", null,
                SessionExerciseStatus.DONE, 1, 8, 10, 2, "Pegada mais fechada", List.of(working("45", 8)));
        Report.Observation pain = new Report.Observation(ObservationCategory.PAIN, BodyRegion.SHOULDER, BodySide.RIGHT,
                6, true, "Supino", "incomodou na descida", Instant.now());
        Report.Observation sleep = new Report.Observation(ObservationCategory.SLEEP, null, null, null, false, null,
                "Dormi 5 horas", Instant.now());
        Report.Observation general = new Report.Observation(ObservationCategory.GENERAL, null, null, null, false, null,
                "Academia cheia", Instant.now());
        Report.Session session = session(MONDAY, false, "Bom treino.", List.of(bench), List.of(pain, sleep, general));

        assertThat(ReportText.notes(session)).containsExactly(
                "Bom treino.",
                "Supino: Pegada mais fechada",
                "[Dor] Ombro D, intensidade 6, interrompeu o exercício (Supino): incomodou na descida",
                "[Sono] Dormi 5 horas",
                "Academia cheia");
    }

    @Test
    void freeSessionAndDeloadTitles() {
        Report.Session session = session(MONDAY, true, null, List.of(), List.of());
        Report avulso = new Report(ReportScope.SESSION, null, null, MONDAY, MONDAY, List.of(new Report.Week(
                null, false, null, null, null, List.of(new Report.Day(DayOfWeek.MONDAY, "Treino avulso", null, session)))));
        String text = ReportText.text(avulso, WeightUnit.KG);
        assertThat(text).startsWith(ReportText.RULE + "\nTREINO AVULSO\n" + ReportText.RULE + "\n\nSEG — TREINO AVULSO (em andamento)\n");
        assertThat(text).doesNotContain("SEMANA").doesNotContain("Peso:");

        Report deload = new Report(ReportScope.WEEK, "Plano", "Fase 1", MONDAY, MONDAY, List.of(
                new Report.Week(8, true, MONDAY, MONDAY.plusDays(6), null, List.of())));
        assertThat(ReportText.text(deload, WeightUnit.KG)).contains("SEMANA 8 — DELOAD\n\nNenhum treino registrado.");
    }

    // ------------------------------------------------------------------ apoio

    private static String line(Report.Exercise exercise) {
        return ReportText.exerciseLine(exercise, true, WeightUnit.KG);
    }

    private static Report.Session session(
            LocalDate date, boolean inProgress, String notes, List<Report.Exercise> exercises, List<Report.Observation> observations) {
        return new Report.Session(UUID.randomUUID(), date, Instant.now(), inProgress, notes, exercises, observations);
    }

    private static Report.Exercise exercise(String name, Integer plannedSets, Report.Set... sets) {
        return new Report.Exercise(UUID.randomUUID(), UUID.randomUUID(), name, "CHEST", null,
                sets.length == 0 ? SessionExerciseStatus.PENDING : SessionExerciseStatus.DONE,
                plannedSets, 8, 12, 2, null, List.of(sets));
    }

    private static Report.Exercise withStatus(Report.Exercise e, SessionExerciseStatus status) {
        return new Report.Exercise(e.sessionExerciseId(), e.exerciseId(), e.name(), e.muscleGroup(), e.replaces(), status,
                e.plannedSets(), e.repsMin(), e.repsMax(), e.targetRir(), e.notes(), e.sets());
    }

    private static Report.Exercise replaces(Report.Exercise e, String original) {
        return new Report.Exercise(e.sessionExerciseId(), e.exerciseId(), e.name(), e.muscleGroup(), original, e.status(),
                e.plannedSets(), e.repsMin(), e.repsMax(), e.targetRir(), e.notes(), e.sets());
    }

    private static Report.Set working(String load, Integer reps) {
        return set(SetType.WORKING, SetStatus.COMPLETED, load, reps);
    }

    private static Report.Set set(SetType type, SetStatus status, String load, Integer reps) {
        return new Report.Set(1, type, status, load == null ? null : new BigDecimal(load), reps, null, null, List.of());
    }

    private static Report.Set withExtension(Report.Set set, Report.Set extension) {
        List<Report.Set> extensions = new ArrayList<>(set.extensions());
        extensions.add(extension);
        return new Report.Set(set.number(), set.type(), set.status(), set.loadKg(), set.reps(), set.rir(), set.notes(), extensions);
    }
}
