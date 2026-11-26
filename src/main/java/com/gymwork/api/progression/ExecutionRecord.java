package com.gymwork.api.progression;

import com.gymwork.api.workout.SessionExerciseStatus;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma execução de um exercício numa sessão finalizada, com a configuração planejada copiada na
 * sessão e as séries registradas. É a unidade de comparação da evolução.
 *
 * @param painReported dor (ou desconforto relevante) registrada para o exercício nesta sessão
 */
public record ExecutionRecord(
        UUID sessionId,
        UUID sessionExerciseId,
        UUID exerciseId,
        Instant performedAt,
        String dayName,
        UUID phaseId,
        String phaseName,
        Integer weekNumber,
        boolean deload,
        SessionExerciseStatus status,
        Integer plannedSets,
        Integer repsMin,
        Integer repsMax,
        Integer targetRir,
        List<SetRecord> sets,
        boolean painReported) {

    public record SetRecord(SetType type, SetStatus status, BigDecimal loadKg, Integer reps, Integer rir) {

        /** Série válida para progressão e contagem: trabalho ou adicional, concluída. */
        public boolean isValid() {
            return status == SetStatus.COMPLETED && type.countsAsSet();
        }

        /** Entra no volume de carga: válida, ou drop set / rest-pause concluídos (A13). */
        boolean countsInVolume() {
            return status == SetStatus.COMPLETED && !type.isPreparation();
        }

        BigDecimal load() {
            return loadKg == null ? BigDecimal.ZERO : loadKg;
        }
    }

    public List<SetRecord> validSets() {
        return sets.stream().filter(SetRecord::isValid).toList();
    }

    /**
     * Execução completa: fez ao menos as séries planejadas, sem série de trabalho interrompida ou
     * não realizada, e o exercício não foi pulado nem substituído. Treino incompleto não é
     * comparado como se fosse queda de desempenho.
     */
    public boolean isComplete() {
        if (status == SessionExerciseStatus.SKIPPED || status == SessionExerciseStatus.SUBSTITUTED) {
            return false;
        }
        boolean missedWorkingSet = sets.stream()
                .anyMatch(set -> set.type().countsAsSet() && set.status() != SetStatus.COMPLETED);
        int valid = validSets().size();
        return valid > 0 && !missedWorkingSet && (plannedSets == null || valid >= plannedSets);
    }

    /** Maior carga entre as séries válidas (peso corporal conta como 0). */
    public BigDecimal topLoad() {
        return validSets().stream().map(SetRecord::load).max(Comparator.naturalOrder()).orElse(null);
    }

    /** Repetições das séries válidas feitas com a maior carga. */
    public List<Integer> repsAtTopLoad() {
        BigDecimal top = topLoad();
        return validSets().stream()
                .filter(set -> set.load().compareTo(top) == 0)
                .map(SetRecord::reps)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> validReps() {
        return validSets().stream().map(SetRecord::reps).filter(Objects::nonNull).toList();
    }

    public int totalReps() {
        return validReps().stream().mapToInt(Integer::intValue).sum();
    }

    /** Volume de carga (kg × reps) das séries válidas mais drop sets e rest-pause. */
    public BigDecimal volumeKg() {
        return sets.stream()
                .filter(SetRecord::countsInVolume)
                .filter(set -> set.reps() != null)
                .map(set -> set.load().multiply(BigDecimal.valueOf(set.reps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Melhor 1RM estimado (Epley) das séries válidas, só como indicador de tendência. */
    public BigDecimal bestEstimatedOneRepMax() {
        return validSets().stream()
                .filter(set -> set.reps() != null && set.reps() > 0 && set.loadKg() != null)
                .map(set -> set.loadKg().multiply(BigDecimal.ONE.add(
                        BigDecimal.valueOf(set.reps()).divide(BigDecimal.valueOf(30), 4, java.math.RoundingMode.HALF_UP))))
                .max(Comparator.naturalOrder())
                .map(value -> value.setScale(1, java.math.RoundingMode.HALF_UP))
                .orElse(null);
    }

    /** "45 kg — 8/7/6/5" (ou "45 kg — 8/8 · 40 kg — 10" se houver cargas diferentes). */
    public String summary() {
        List<SetRecord> valid = validSets();
        if (valid.isEmpty()) {
            return "sem séries válidas";
        }
        StringBuilder text = new StringBuilder();
        BigDecimal current = null;
        for (SetRecord set : valid) {
            if (current == null || set.load().compareTo(current) != 0) {
                if (current != null) {
                    text.append(" · ");
                }
                current = set.load();
                text.append(formatLoad(set.loadKg())).append(" — ");
            } else {
                text.append('/');
            }
            text.append(set.reps() == null ? "?" : set.reps());
        }
        return text.toString();
    }

    static String formatLoad(BigDecimal kg) {
        if (kg == null || kg.signum() == 0) {
            return "peso corporal";
        }
        return kg.stripTrailingZeros().toPlainString().replace('.', ',') + " kg";
    }
}
