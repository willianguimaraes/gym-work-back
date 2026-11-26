package com.gymwork.api.workout;

/** PENDING/PARTIAL/DONE são calculados pelas séries válidas; SKIPPED e SUBSTITUTED vêm de ações do usuário. */
public enum SessionExerciseStatus {
    PENDING,
    PARTIAL,
    DONE,
    SKIPPED,
    SUBSTITUTED
}
