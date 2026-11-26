package com.gymwork.api.exercise;

/** Filtros da busca no catálogo (todos opcionais). */
public record ExerciseSearch(
        String text, String muscleGroup, Equipment equipment, ExerciseScope scope, boolean includeInactive) {

    public ExerciseSearch {
        if (scope == null) {
            scope = ExerciseScope.ALL;
        }
    }
}
