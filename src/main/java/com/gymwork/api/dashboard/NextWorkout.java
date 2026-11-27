package com.gymwork.api.dashboard;

import com.gymwork.api.workout.dto.StartOptionsResponse.DayOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolhe o próximo treino da fase atual. Com dias da semana definidos: o próximo dia agendado a
 * partir de hoje (o de hoje, se ainda não foi feito). Sem dias definidos: o dia seguinte ao último
 * feito, em rodízio; nunca feito, o primeiro.
 */
public final class NextWorkout {

    public enum Reason {
        TODAY,
        SCHEDULED,
        ROTATION
    }

    public record Choice(DayOption day, Reason reason, LocalDate date) {
    }

    private NextWorkout() {
    }

    public static Optional<Choice> choose(List<DayOption> days, LocalDate today, ZoneId zone) {
        if (days.isEmpty()) {
            return Optional.empty();
        }
        List<DayOption> scheduled = days.stream().filter(day -> day.dayOfWeek() != null).toList();
        if (!scheduled.isEmpty()) {
            return scheduled.stream()
                    .map(day -> new Choice(day, null, nextDate(day, today, zone)))
                    .min(Comparator.comparing(Choice::date))
                    .map(choice -> new Choice(
                            choice.day(), choice.date().equals(today) ? Reason.TODAY : Reason.SCHEDULED, choice.date()));
        }
        DayOption last = days.stream()
                .filter(day -> day.lastPerformedAt() != null)
                .max(Comparator.comparing(DayOption::lastPerformedAt))
                .orElse(null);
        DayOption next = last == null ? days.get(0) : days.get((days.indexOf(last) + 1) % days.size());
        return Optional.of(new Choice(next, Reason.ROTATION, null));
    }

    /** Hoje, se for o dia e ele ainda não foi feito hoje; senão a próxima ocorrência do dia. */
    private static LocalDate nextDate(DayOption day, LocalDate today, ZoneId zone) {
        DayOfWeek dayOfWeek = day.dayOfWeek();
        if (today.getDayOfWeek() == dayOfWeek && !performedOn(day.lastPerformedAt(), today, zone)) {
            return today;
        }
        return today.with(TemporalAdjusters.next(dayOfWeek));
    }

    private static boolean performedOn(Instant at, LocalDate date, ZoneId zone) {
        return at != null && at.atZone(zone).toLocalDate().equals(date);
    }
}
