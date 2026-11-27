package com.gymwork.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.dashboard.NextWorkout.Choice;
import com.gymwork.api.dashboard.NextWorkout.Reason;
import com.gymwork.api.workout.dto.StartOptionsResponse.DayOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NextWorkoutTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Quinta-feira. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void scheduledDayOfTodayComesFirstUntilItIsDone() {
        DayOption upper = day("UPPER", DayOfWeek.THURSDAY, null);
        DayOption lower = day("LOWER", DayOfWeek.FRIDAY, null);

        Choice choice = NextWorkout.choose(List.of(lower, upper), TODAY, ZONE).orElseThrow();
        assertThat(choice.day()).isEqualTo(upper);
        assertThat(choice.reason()).isEqualTo(Reason.TODAY);
        assertThat(choice.date()).isEqualTo(TODAY);

        DayOption upperDone = day("UPPER", DayOfWeek.THURSDAY, Instant.parse("2026-11-26T10:00:00Z"));
        Choice next = NextWorkout.choose(List.of(upperDone, lower), TODAY, ZONE).orElseThrow();
        assertThat(next.day()).isEqualTo(lower);
        assertThat(next.reason()).isEqualTo(Reason.SCHEDULED);
        assertThat(next.date()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void scheduleWrapsToNextWeek() {
        DayOption monday = day("A", DayOfWeek.MONDAY, null);
        DayOption tuesday = day("B", DayOfWeek.TUESDAY, null);
        Choice choice = NextWorkout.choose(List.of(monday, tuesday), TODAY, ZONE).orElseThrow();
        assertThat(choice.day()).isEqualTo(monday);
        assertThat(choice.date()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void withoutScheduleRotatesAfterTheLastDoneDay() {
        DayOption a = day("A", null, Instant.parse("2026-11-20T10:00:00Z"));
        DayOption b = day("B", null, Instant.parse("2026-11-24T10:00:00Z"));
        DayOption c = day("C", null, null);
        assertThat(NextWorkout.choose(List.of(a, b, c), TODAY, ZONE).orElseThrow().day()).isEqualTo(c);

        DayOption cDone = day("C", null, Instant.parse("2026-11-25T10:00:00Z"));
        Choice wrap = NextWorkout.choose(List.of(a, b, cDone), TODAY, ZONE).orElseThrow();
        assertThat(wrap.day()).isEqualTo(a);
        assertThat(wrap.reason()).isEqualTo(Reason.ROTATION);

        DayOption fresh = day("A", null, null);
        assertThat(NextWorkout.choose(List.of(fresh, c), TODAY, ZONE).orElseThrow().day()).isEqualTo(fresh);
        assertThat(NextWorkout.choose(List.of(), TODAY, ZONE)).isEmpty();
    }

    private static DayOption day(String name, DayOfWeek dayOfWeek, Instant last) {
        return new DayOption(UUID.nameUUIDFromBytes(name.getBytes()), name, dayOfWeek, 3, last);
    }
}
