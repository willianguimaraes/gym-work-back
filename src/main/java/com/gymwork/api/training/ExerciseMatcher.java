package com.gymwork.api.training;

import com.gymwork.api.common.SearchText;
import com.gymwork.api.exercise.Exercise;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Casa os nomes de exercícios de um arquivo importado com o catálogo do usuário. O nome é comparado
 * sem acentos, maiúsculas ou espaços extras; quando não há um igual, sugere os mais parecidos
 * (palavras em comum ou pequenas diferenças de digitação).
 */
final class ExerciseMatcher {

    static final double MIN_SIMILARITY = 0.3;
    /** Abaixo disso a semelhança de escrita não conta (evita sugerir nomes só por terem letras parecidas). */
    static final double MIN_SPELLING = 0.6;
    private static final Set<String> STOP_WORDS = Set.of("de", "da", "do", "das", "dos", "com", "na", "no", "e", "a", "o", "em");

    private final List<Exercise> catalog;
    private final Map<String, List<Exercise>> byName;

    ExerciseMatcher(List<Exercise> catalog) {
        this.catalog = catalog;
        this.byName = catalog.stream().collect(Collectors.groupingBy(exercise -> SearchText.normalize(exercise.getName())));
    }

    /** Exercícios do catálogo com o mesmo nome (pode haver mais de um, com equipamentos diferentes). */
    List<Exercise> sameName(String name) {
        return byName.getOrDefault(SearchText.normalize(name), List.of());
    }

    /** Os mais parecidos, do mais para o menos parecido. */
    List<Exercise> similar(String name, int limit) {
        String target = SearchText.normalize(name);
        return catalog.stream()
                .map(exercise -> Map.entry(exercise, similarity(target, SearchText.normalize(exercise.getName()))))
                .filter(entry -> entry.getValue() >= MIN_SIMILARITY)
                .sorted(Map.Entry.<Exercise, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().getName()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 0..1: o maior entre as palavras em comum (Jaccard) e a semelhança de escrita (Levenshtein),
     * esta só quando alta o bastante para indicar erro de digitação.
     */
    static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> wordsA = words(a);
        Set<String> wordsB = words(b);
        double jaccard = 0;
        if (!wordsA.isEmpty() && !wordsB.isEmpty()) {
            Set<String> common = new HashSet<>(wordsA);
            common.retainAll(wordsB);
            Set<String> all = new HashSet<>(wordsA);
            all.addAll(wordsB);
            jaccard = (double) common.size() / all.size();
        }
        double spelling = 1 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        return Math.max(jaccard, spelling >= MIN_SPELLING ? spelling : 0);
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.split("[\\s\\-/()]+"))
                .filter(word -> word.length() > 1 && !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
