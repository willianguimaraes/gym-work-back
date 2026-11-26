package com.gymwork.api.exercise;

/** Filtro de origem na busca do catálogo. */
public enum ExerciseScope {
    /** Globais + personalizados do usuário. */
    ALL,
    /** Só o catálogo global. */
    GLOBAL,
    /** Só os personalizados do usuário. */
    MINE
}
