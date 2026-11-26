package com.gymwork.api.report;

import com.gymwork.api.user.WeightUnit;
import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Relatório no formato textual de acompanhamento (seção 5 do prompt). Gera linhas com um tipo, para
 * que o TXT e o PDF usem exatamente o mesmo conteúdo.
 *
 * <pre>
 * ══════════════════════
 * FASE 4 — CONTROLE
 * ══════════════════════
 *
 * SEMANA 1
 * Peso: 100 kg
 *
 * SEG — UPPER
 *
 * Supino inclinado máquina: 45 kg | 8/8/7/6
 *
 * Observações:
 * Treino concluído normalmente.
 * ________________________________
 * </pre>
 */
public final class ReportText {

    public static final String RULE = "══════════════════════";
    public static final String SEPARATOR = "________________________________";
    private static final String NOT_DONE = "x";

    private ReportText() {
    }

    public enum Kind {
        RULE,
        TITLE,
        WEEK,
        DAY,
        TEXT,
        BLANK,
        SEPARATOR
    }

    public record Line(Kind kind, String text) {
    }

    public static String text(Report report, WeightUnit unit) {
        return join(lines(report, unit));
    }

    public static String join(List<Line> lines) {
        return lines.stream().map(Line::text).collect(Collectors.joining("\n")).stripTrailing() + "\n";
    }

    public static List<Line> lines(Report report, WeightUnit unit) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Kind.RULE, RULE));
        lines.add(new Line(Kind.TITLE, report.phaseName() == null ? "TREINO AVULSO" : upper(report.phaseName())));
        lines.add(new Line(Kind.RULE, RULE));
        blank(lines);
        for (Report.Week week : report.weeks()) {
            String weekTitle = weekTitle(report, week);
            if (weekTitle != null) {
                lines.add(new Line(Kind.WEEK, weekTitle));
            }
            if (week.bodyWeightKg() != null) {
                lines.add(new Line(Kind.TEXT, "Peso: " + ReportLabels.load(week.bodyWeightKg(), unit)));
            }
            if (weekTitle != null || week.bodyWeightKg() != null) {
                blank(lines);
            }
            if (week.days().isEmpty()) {
                lines.add(new Line(Kind.TEXT, "Nenhum treino registrado."));
                blank(lines);
            }
            for (Report.Day day : week.days()) {
                day(lines, day, unit);
            }
        }
        return lines;
    }

    private static String weekTitle(Report report, Report.Week week) {
        if (week.number() == null) {
            return report.scope() == ReportScope.PHASE ? "SEM SEMANA DEFINIDA" : null;
        }
        return "SEMANA " + week.number() + (week.deload() ? " — DELOAD" : "");
    }

    private static void day(List<Line> lines, Report.Day day, WeightUnit unit) {
        String weekday = ReportLabels.weekday(day.weekday());
        String title = (weekday == null ? "" : weekday + " — ") + upper(day.name());
        Report.Session session = day.session();
        if (session != null && session.inProgress()) {
            title += " (em andamento)";
        }
        lines.add(new Line(Kind.DAY, title));
        blank(lines);
        if (session == null) {
            lines.add(new Line(Kind.TEXT, "Não realizado"));
        } else {
            if (session.exercises().isEmpty()) {
                lines.add(new Line(Kind.TEXT, "Nenhum exercício registrado"));
            }
            for (Report.Exercise exercise : session.exercises()) {
                lines.add(new Line(Kind.TEXT, exerciseLine(exercise, !session.inProgress(), unit)));
            }
            blank(lines);
            lines.add(new Line(Kind.TEXT, "Observações:"));
            for (String note : notes(session)) {
                lines.add(new Line(Kind.TEXT, note));
            }
        }
        lines.add(new Line(Kind.SEPARATOR, SEPARATOR));
        blank(lines);
    }

    /** Observações do dia: anotação da sessão, anotações dos exercícios e observações registradas. */
    static List<String> notes(Report.Session session) {
        List<String> notes = new ArrayList<>();
        if (session.notes() != null && !session.notes().isBlank()) {
            notes.addAll(session.notes().strip().lines().toList());
        }
        for (Report.Exercise exercise : session.exercises()) {
            if (exercise.notes() != null && !exercise.notes().isBlank()) {
                notes.add(exercise.name() + ": " + exercise.notes().strip().replace('\n', ' '));
            }
        }
        for (Report.Observation observation : session.observations()) {
            String line = ReportLabels.observation(observation);
            if (!line.isBlank()) {
                notes.add(line.replace('\n', ' '));
            }
        }
        return notes;
    }

    /**
     * "Supino: 45 kg | 8/8/7/6". Cargas diferentes viram grupos ("45 kg | 8/8 · 40 kg | 7/6"); série
     * não feita é "x" (inclusive as planejadas que não chegaram a ser registradas, depois de
     * finalizada a sessão); drop set e rest-pause aparecem como "8+4"; sem carga, só as repetições.
     */
    public static String exerciseLine(Report.Exercise exercise, boolean finished, WeightUnit unit) {
        String name = exercise.replaces() == null ? exercise.name() : exercise.replaces() + " → " + exercise.name();
        if (exercise.status() == SessionExerciseStatus.SKIPPED) {
            return name + ": não realizado";
        }
        List<Token> tokens = new ArrayList<>();
        for (Report.Set set : exercise.sets()) {
            if (!set.type().countsAsSet() || set.status() == SetStatus.SUBSTITUTED) {
                continue;
            }
            tokens.add(new Token(set.loadKg(), set.status() == SetStatus.COMPLETED ? reps(set) : NOT_DONE,
                    set.status() == SetStatus.COMPLETED));
        }
        if (tokens.isEmpty()) {
            return name + ": " + (finished ? "não realizado" : "pendente");
        }
        if (finished && exercise.plannedSets() != null) {
            while (tokens.size() < exercise.plannedSets()) {
                tokens.add(new Token(null, NOT_DONE, false));
            }
        }
        return name + ": " + groups(tokens, unit);
    }

    private record Token(BigDecimal loadKg, String text, boolean done) {
    }

    /** Série não feita sem carga herda a carga do grupo anterior (ou do seguinte, se for a primeira). */
    private static String groups(List<Token> tokens, WeightUnit unit) {
        List<BigDecimal> loads = new ArrayList<>();
        BigDecimal previous = null;
        for (Token token : tokens) {
            BigDecimal load = token.loadKg() != null || token.done() ? token.loadKg() : previous;
            loads.add(load);
            previous = load;
        }
        for (int i = loads.size() - 2; i >= 0; i--) {
            if (loads.get(i) == null && !tokens.get(i).done()) {
                loads.set(i, loads.get(i + 1));
            }
        }
        StringBuilder text = new StringBuilder();
        BigDecimal current = null;
        boolean first = true;
        for (int i = 0; i < tokens.size(); i++) {
            BigDecimal load = normalize(loads.get(i));
            if (first || !Objects.equals(load, current)) {
                if (!first) {
                    text.append(" · ");
                }
                if (load != null) {
                    text.append(ReportLabels.load(load, unit)).append(" | ");
                }
                current = load;
                first = false;
            } else {
                text.append('/');
            }
            text.append(tokens.get(i).text());
        }
        return text.toString();
    }

    /** Peso corporal (sem carga ou zero) não leva carga no texto; compara cargas sem escala. */
    private static BigDecimal normalize(BigDecimal load) {
        return load == null || load.signum() == 0 ? null : load.stripTrailingZeros();
    }

    private static String reps(Report.Set set) {
        StringBuilder text = new StringBuilder(set.reps() == null ? "?" : String.valueOf(set.reps()));
        for (Report.Set extension : set.extensions()) {
            if (extension.status() == SetStatus.COMPLETED && extension.reps() != null) {
                text.append('+').append(extension.reps());
            }
        }
        return text.toString();
    }

    private static String upper(String text) {
        return text.toUpperCase(new Locale("pt", "BR"));
    }

    private static void blank(List<Line> lines) {
        lines.add(new Line(Kind.BLANK, ""));
    }
}
