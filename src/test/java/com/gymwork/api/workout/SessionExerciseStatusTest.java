package com.gymwork.api.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do status do exercício na sessão e dos tipos de série (A13). */
class SessionExerciseStatusTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void statusFollowsCompletedValidSets() {
        SessionExercise exercise = planned(3);

        exercise.refreshStatus(0);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.PENDING);
        exercise.refreshStatus(2);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.PARTIAL);
        exercise.refreshStatus(3);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.DONE);
        exercise.refreshStatus(4);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.DONE);
    }

    @Test
    void extraExerciseWithoutPlanIsDoneWithOneSet() {
        SessionExercise extra = new SessionExercise(session(), exercise(), 0);

        extra.refreshStatus(1);

        assertThat(extra.getStatus()).isEqualTo(SessionExerciseStatus.DONE);
    }

    @Test
    void skipStaysUntilASetIsRecordedAndSubstitutionIsFinal() {
        SessionExercise exercise = planned(3);
        exercise.skip();
        exercise.refreshStatus(0);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.SKIPPED);

        exercise.unskip();
        exercise.refreshStatus(1);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.PARTIAL);

        SessionExercise replacement = new SessionExercise(session(), exercise(), 1);
        exercise.substitutedBy(replacement);
        exercise.refreshStatus(3);
        assertThat(exercise.getStatus()).isEqualTo(SessionExerciseStatus.SUBSTITUTED);
    }

    @Test
    void warmupsActivationsDropSetsAndRestPauseAreNotCountedAsSets() {
        assertThat(SetType.WORKING.countsAsSet()).isTrue();
        assertThat(SetType.EXTRA.countsAsSet()).isTrue();
        assertThat(SetType.WARMUP.countsAsSet()).isFalse();
        assertThat(SetType.ACTIVATION.countsAsSet()).isFalse();
        assertThat(SetType.DROP_SET.countsAsSet()).isFalse();
        assertThat(SetType.REST_PAUSE.countsAsSet()).isFalse();

        assertThat(SetType.WARMUP.isPreparation()).isTrue();
        assertThat(SetType.ACTIVATION.isPreparation()).isTrue();
        assertThat(SetType.DROP_SET.isPreparation()).isFalse();
    }

    @Test
    void onlyCompletedValidSetsCount() {
        SessionExercise exercise = planned(3);
        ExerciseSet done = set(exercise, SetType.WORKING, SetStatus.COMPLETED);
        ExerciseSet interrupted = set(exercise, SetType.WORKING, SetStatus.INTERRUPTED);
        ExerciseSet warmup = set(exercise, SetType.WARMUP, SetStatus.COMPLETED);

        assertThat(done.countsAsCompletedSet()).isTrue();
        assertThat(interrupted.countsAsCompletedSet()).isFalse();
        assertThat(warmup.countsAsCompletedSet()).isFalse();
    }

    @Test
    void finishNeverEndsBeforeTheStart() {
        Instant start = Instant.parse("2026-11-26T10:00:00Z");
        WorkoutSession session = new WorkoutSession(USER, start);

        session.finish(start.minusSeconds(30), null);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getFinishedAt()).isEqualTo(start);
    }

    private static SessionExercise planned(int sets) {
        SessionExercise exercise = new SessionExercise(session(), exercise(), 0);
        exercise.copyPlan(UUID.randomUUID(), new PlannedSnapshot(sets, 8, 12, 90, 2, new BigDecimal("40")));
        return exercise;
    }

    private static ExerciseSet set(SessionExercise exercise, SetType type, SetStatus status) {
        ExerciseSet set = new ExerciseSet(exercise, null, 1, Instant.now());
        set.record(new SetData(type, null, status, new BigDecimal("40"), 10, null, null, null));
        return set;
    }

    private static WorkoutSession session() {
        return new WorkoutSession(USER, Instant.now());
    }

    private static Exercise exercise() {
        Exercise exercise = Exercise.custom(USER);
        exercise.update("Supino", "CHEST", Set.of(), ExerciseType.COMPOUND, Equipment.BARBELL, null, null);
        return exercise;
    }
}
