package com.gymwork.api.report;

import com.gymwork.api.workout.SessionExerciseStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Relatório em CSV: uma linha por série (inclusive aquecimento, drop set e rest-pause), para
 * planilha. Separador ";" e vírgula decimal (padrão do Excel em português), cargas sempre em kg e
 * BOM UTF-8 para os acentos abrirem certo.
 */
public final class ReportCsv {

    static final List<String> HEADER = List.of(
            "data", "semana", "dia", "exercicio", "substituiu", "serie", "tipo", "carga_kg", "reps", "rir",
            "status", "observacao");

    private ReportCsv() {
    }

    public static String csv(Report report) {
        List<String> rows = new ArrayList<>();
        rows.add(String.join(";", HEADER));
        for (Report.Week week : report.weeks()) {
            String weekNumber = week.number() == null ? "" : String.valueOf(week.number());
            for (Report.Day day : week.days()) {
                Report.Session session = day.session();
                String dayName = day.name();
                if (session == null) {
                    rows.add(row("", weekNumber, dayName, "", "", "", "", null, null, null, "Dia não realizado", ""));
                    continue;
                }
                String date = ReportLabels.date(session.date());
                for (Report.Exercise exercise : session.exercises()) {
                    String replaces = exercise.replaces() == null ? "" : exercise.replaces();
                    if (exercise.sets().isEmpty()) {
                        String status = exercise.status() == SessionExerciseStatus.SKIPPED ? "Pulado" : "Sem séries";
                        rows.add(row(date, weekNumber, dayName, exercise.name(), replaces, "", "", null, null, null,
                                status, exercise.notes()));
                        continue;
                    }
                    int number = 0;
                    for (Report.Set set : exercise.sets()) {
                        String setNumber = set.type().countsAsSet() ? String.valueOf(++number) : "";
                        rows.add(setRow(date, weekNumber, dayName, exercise, replaces, setNumber, set));
                        for (Report.Set extension : set.extensions()) {
                            rows.add(setRow(date, weekNumber, dayName, exercise, replaces, setNumber, extension));
                        }
                    }
                }
            }
        }
        return "﻿" + String.join("\r\n", rows) + "\r\n";
    }

    private static String setRow(
            String date, String week, String day, Report.Exercise exercise, String replaces, String number, Report.Set set) {
        return row(date, week, day, exercise.name(), replaces, number, ReportLabels.setType(set.type()), set.loadKg(),
                set.reps(), set.rir(), ReportLabels.setStatus(set.status()), set.notes());
    }

    private static String row(
            String date, String week, String day, String exercise, String replaces, String number, String type,
            BigDecimal loadKg, Integer reps, Integer rir, String status, String notes) {
        return List.of(
                        date, week, day, exercise, replaces, number, type,
                        loadKg == null ? "" : ReportLabels.number(loadKg),
                        reps == null ? "" : String.valueOf(reps),
                        rir == null ? "" : String.valueOf(rir),
                        status,
                        notes == null ? "" : notes)
                .stream()
                .map(ReportCsv::escape)
                .collect(Collectors.joining(";"));
    }

    /** Aspas quando há separador, aspas ou quebra de linha; neutraliza fórmulas (=, +, -, @). */
    static String escape(String value) {
        String text = value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !text.matches("-?\\d+(,\\d+)?")) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
