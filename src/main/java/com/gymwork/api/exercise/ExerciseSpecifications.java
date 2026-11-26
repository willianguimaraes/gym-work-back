package com.gymwork.api.exercise;

import com.gymwork.api.common.SearchText;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filtros da busca no catálogo. Todos partem de {@link #visibleTo}, que garante o isolamento. */
final class ExerciseSpecifications {

    private ExerciseSpecifications() {
    }

    static Specification<Exercise> visibleTo(UUID userId, ExerciseScope scope) {
        return (root, query, cb) -> switch (scope) {
            case GLOBAL -> cb.isNull(root.get("ownerId"));
            case MINE -> cb.equal(root.get("ownerId"), userId);
            case ALL -> cb.or(cb.isNull(root.get("ownerId")), cb.equal(root.get("ownerId"), userId));
        };
    }

    /** Cada palavra da busca precisa aparecer no nome (ordem livre, sem acento). */
    static Specification<Exercise> nameContains(String search) {
        String normalized = SearchText.normalize(search);
        if (normalized.isEmpty()) {
            return null;
        }
        List<Specification<Exercise>> words = new ArrayList<>();
        for (String word : normalized.split(" ")) {
            String pattern = "%" + escapeLike(word) + "%";
            words.add((root, query, cb) -> cb.like(root.get("searchName"), pattern, '\\'));
        }
        return Specification.allOf(words);
    }

    static Specification<Exercise> muscleGroup(String code) {
        return code == null || code.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("primaryMuscleGroup"), code);
    }

    static Specification<Exercise> equipment(Equipment equipment) {
        return equipment == null ? null : (root, query, cb) -> cb.equal(root.get("equipment"), equipment);
    }

    static Specification<Exercise> activeOnly(boolean includeInactive) {
        return includeInactive ? null : (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
