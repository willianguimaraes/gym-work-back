package com.gymwork.api.progression.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Volume e frequência por semana (segunda a domingo, no fuso do usuário). Séries válidas contam para
 * o grupo principal do exercício ({@code sets}) e, à parte, para os secundários
 * ({@code secondarySets}), sem pesos arbitrários. Aquecimento e ativação não contam.
 */
public record VolumeReport(LocalDate from, LocalDate to, List<Week> weeks, List<MuscleVolume> totals) {

    public record Week(
            LocalDate weekStart,
            int sessions,
            int validSets,
            BigDecimal volumeKg,
            List<MuscleVolume> muscles) {
    }

    public record MuscleVolume(String muscleGroup, int sets, int secondarySets) {
    }
}
