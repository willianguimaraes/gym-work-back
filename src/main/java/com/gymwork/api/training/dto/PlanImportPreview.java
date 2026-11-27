package com.gymwork.api.training.dto;

import com.gymwork.api.exercise.Equipment;
import com.gymwork.api.exercise.Exercise;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da validação de uma importação, antes de gravar.
 *
 * @param ready {@code true} quando não há erros e todos os exercícios estão definidos
 * @param template o plano lido (normalizado); {@code null} quando há erros
 * @param targetPlan planejamento que vai receber as fases ({@code null} = um novo rascunho)
 */
public record PlanImportPreview(
        boolean ready,
        List<Issue> errors,
        List<Issue> warnings,
        PlanTemplate template,
        List<ExerciseMatch> exercises,
        TargetPlan targetPlan) {

    /**
     * @param path caminho no JSON (ex.: {@code phases[0].days[1].exercises[2].repsMax})
     * @param location o mesmo caminho em palavras (ex.: "Fase 1 › Dia 2 (LOWER) › Exercício 3")
     */
    public record Issue(String path, String location, String message) {
    }

    public enum MatchStatus {
        /** Nome encontrado no catálogo. */
        MATCHED,
        /** Escolhido pelo usuário. */
        CHOSEN,
        /** Mais de um exercício com esse nome (equipamentos diferentes): o usuário escolhe. */
        AMBIGUOUS,
        /** Não está no catálogo: o usuário escolhe um parecido ou cria um personalizado. */
        NOT_FOUND
    }

    /**
     * @param name nome como está no arquivo (chave para {@link PlanImportRequest#exercises()})
     * @param exercise o exercício que será usado (quando MATCHED ou CHOSEN)
     * @param candidates opções para escolher (AMBIGUOUS) ou nomes parecidos (NOT_FOUND)
     * @param uses quantas vezes aparece no plano
     */
    public record ExerciseMatch(
            String name, MatchStatus status, Candidate exercise, List<Candidate> candidates, int uses) {

        public boolean resolved() {
            return status == MatchStatus.MATCHED || status == MatchStatus.CHOSEN;
        }
    }

    public record Candidate(UUID id, String name, String primaryMuscleGroup, Equipment equipment, boolean custom) {

        public static Candidate from(Exercise exercise) {
            return new Candidate(
                    exercise.getId(),
                    exercise.getName(),
                    exercise.getPrimaryMuscleGroup(),
                    exercise.getEquipment(),
                    !exercise.isGlobal());
        }
    }

    public record TargetPlan(UUID id, String name, int phases) {
    }
}
