package com.gymwork.api.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExerciseMatcherTest {

    private static final UUID USER = UUID.randomUUID();

    private final Exercise inclineMachine = exercise("Supino inclinado máquina", Equipment.MACHINE);
    private final Exercise inclineSmith = exercise("Supino inclinado máquina", Equipment.SMITH);
    private final Exercise lateral = exercise("Elevação lateral com halteres", Equipment.DUMBBELL);
    private final Exercise squat = exercise("Agachamento livre", Equipment.BARBELL);
    private final Exercise facePull = exercise("Face pull", Equipment.CABLE);
    private final Exercise triceps = exercise("Tríceps corda", Equipment.CABLE);
    private final Exercise legs = exercise("Elevação de pernas", Equipment.BODYWEIGHT);
    private final ExerciseMatcher matcher =
            new ExerciseMatcher(List.of(inclineMachine, inclineSmith, lateral, squat, facePull, triceps, legs));

    @Test
    void sameNameIgnoresAccentsCaseAndSpaces() {
        assertThat(matcher.sameName("  agachamento   LIVRE ")).containsExactly(squat);
        assertThat(matcher.sameName("supino inclinado maquina")).containsExactly(inclineMachine, inclineSmith);
        assertThat(matcher.sameName("Supino reto")).isEmpty();
    }

    @Test
    void suggestsSimilarNamesForWordsInCommonAndTypos() {
        assertThat(matcher.similar("Elevação lateral halteres", 5)).first().isEqualTo(lateral);
        assertThat(matcher.similar("Agachamentto livre", 5)).first().isEqualTo(squat);
        assertThat(matcher.similar("Remada curvada", 5)).isEmpty();
        // só uma palavra em comum, ou letras parecidas por acaso, não viram sugestão
        assertThat(matcher.similar("Face pull com corda", 5)).containsExactly(facePull);
        assertThat(matcher.similar("Elevação lateral halteres", 5)).doesNotContain(legs);
    }

    private static Exercise exercise(String name, Equipment equipment) {
        Exercise exercise = Exercise.custom(USER);
        exercise.update(name, "CHEST", Set.of(), ExerciseType.COMPOUND, equipment, null, null);
        return exercise;
    }
}
