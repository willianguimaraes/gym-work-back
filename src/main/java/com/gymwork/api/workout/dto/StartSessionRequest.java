package com.gymwork.api.workout.dto;

import java.util.UUID;

/**
 * Início de uma sessão. Sem {@code workoutDayId}: treino avulso (exercícios adicionados na hora).
 * Sem {@code weekId}: a semana é escolhida pela data de hoje, quando a fase tem data de início.
 */
public record StartSessionRequest(UUID workoutDayId, UUID weekId) {
}
