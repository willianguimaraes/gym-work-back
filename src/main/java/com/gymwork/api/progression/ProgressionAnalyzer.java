package com.gymwork.api.progression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Análise de progressão de um exercício (regra padrão: dupla progressão, A12). Função pura sobre
 * o histórico; único lugar onde as regras de tendência e sugestão vivem.
 *
 * <p>Regras importantes (seção 9 da arquitetura):
 * <ul>
 *   <li>Considera todas as séries válidas; aquecimento e ativação ficam de fora.</li>
 *   <li>Uma série no teto da faixa não basta: todas as séries válidas precisam chegar lá.</li>
 *   <li>Treino incompleto não é tratado como queda; semana de deload não entra na comparação.</li>
 *   <li>Dor ou desconforto relevante suspende a sugestão de aumento.</li>
 *   <li>As sugestões são só informativas; nada muda o plano sem ação do usuário.</li>
 * </ul>
 */
public final class ProgressionAnalyzer {

    public enum Trend {
        PROGRESSING,
        STABLE,
        REGRESSING,
        INCOMPLETE,
        INSUFFICIENT_DATA
    }

    public enum Suggestion {
        /** Avaliar aumento de carga na próxima sessão. */
        INCREASE_LOAD,
        /** Manter a carga e buscar mais repetições dentro da faixa. */
        ADD_REPS,
        /** Teto atingido, mas ainda sem as sessões seguidas exigidas pela regra. */
        CONFIRM_TOP,
        /** Teto atingido com RIR abaixo do mínimo. */
        REVIEW_RIR,
        /** Desempenho abaixo do anterior. */
        REVIEW_PERFORMANCE,
        /** Dor/desconforto registrado: sem sugestão de aumento. */
        HOLD_PAIN,
        /** Sem dados ou regra para sugerir. */
        NONE
    }

    /** Resultado da análise. {@code suggestedLoadKg} só vem em {@link Suggestion#INCREASE_LOAD}. */
    public record Analysis(
            Trend trend,
            String trendDetail,
            Suggestion suggestion,
            String message,
            BigDecimal suggestedLoadKg,
            ExecutionRecord latest,
            ExecutionRecord previous,
            boolean painReported) {
    }

    private ProgressionAnalyzer() {
    }

    /** @param history execuções do exercício em ordem cronológica (mais antiga primeiro) */
    public static Analysis analyze(List<ExecutionRecord> history, RuleSettings rule) {
        if (history.isEmpty()) {
            return new Analysis(Trend.INSUFFICIENT_DATA, "Ainda não há execuções registradas.",
                    Suggestion.NONE, null, null, null, null, false);
        }
        ExecutionRecord latest = history.get(history.size() - 1);
        List<ExecutionRecord> comparable = history.stream()
                .filter(ExecutionRecord::isComplete)
                .filter(record -> !record.deload())
                .toList();
        ExecutionRecord current = comparable.isEmpty() ? null : comparable.get(comparable.size() - 1);
        ExecutionRecord previous = comparable.size() < 2 ? null : comparable.get(comparable.size() - 2);
        boolean pain = latest.painReported();

        // ---------------------------------------------------------------- tendência
        Trend trend;
        String detail;
        if (!latest.isComplete()) {
            trend = Trend.INCOMPLETE;
            detail = incompleteDetail(latest);
        } else if (latest.deload() && previous == null) {
            trend = Trend.INSUFFICIENT_DATA;
            detail = "Semana de deload: não entra na comparação.";
        } else if (previous == null) {
            trend = Trend.INSUFFICIENT_DATA;
            detail = "Primeira execução completa: ainda não há com o que comparar.";
        } else {
            Comparison comparison = compare(current, previous);
            trend = comparison.trend();
            detail = (latest.deload() ? "Última sessão foi deload (fora da comparação). " : "") + comparison.detail();
        }

        // ---------------------------------------------------------------- sugestão
        if (pain) {
            return new Analysis(trend, detail, Suggestion.HOLD_PAIN,
                    "Dor ou desconforto registrado neste exercício: sem sugestão de aumento de carga. "
                            + "O app não avalia lesões; se persistir, procure orientação profissional.",
                    null, latest, previous, true);
        }
        if (!rule.enabled()) {
            return new Analysis(trend, detail, Suggestion.NONE,
                    "Regra de progressão desativada para este exercício.", null, latest, previous, false);
        }
        if (trend == Trend.INCOMPLETE) {
            return new Analysis(trend, detail, Suggestion.NONE,
                    "Complete as séries planejadas para avaliar a progressão.", null, latest, previous, false);
        }
        if (latest.deload()) {
            return new Analysis(trend, detail, Suggestion.NONE,
                    "Semana de deload: mantenha o planejado.", null, latest, previous, false);
        }
        if (latest.repsMax() == null) {
            return new Analysis(trend, detail, Suggestion.NONE,
                    "Sem faixa de repetições planejada para avaliar o teto.", null, latest, previous, false);
        }
        if (trend == Trend.REGRESSING) {
            return new Analysis(trend, detail, Suggestion.REVIEW_PERFORMANCE,
                    "Desempenho abaixo da execução anterior. Antes de mudar a carga, considere sono, "
                            + "recuperação e execução; uma sessão isolada não define uma queda.",
                    null, latest, previous, false);
        }

        int repsMax = latest.repsMax();
        boolean atTop = reachedTop(latest);
        if (!atTop) {
            long setsAtTop = latest.validReps().stream().filter(reps -> reps >= repsMax).count();
            String partial = setsAtTop > 0
                    ? " %d de %d séries chegaram a %d reps; todas precisam chegar.".formatted(
                            setsAtTop, latest.validSets().size(), repsMax)
                    : "";
            return new Analysis(trend, detail, Suggestion.ADD_REPS,
                    "Mantenha a carga e busque mais repetições dentro da faixa (%s).%s".formatted(
                            range(latest), partial),
                    null, latest, previous, false);
        }

        Integer minRir = rule.minRir() != null ? rule.minRir() : latest.targetRir();
        if (minRir != null && latest.validSets().stream()
                .anyMatch(set -> set.rir() != null && set.rir() < minRir)) {
            return new Analysis(trend, detail, Suggestion.REVIEW_RIR,
                    "Todas as séries chegaram ao teto (%d reps), mas com RIR abaixo de %d. Mantenha a carga até sobrar essa margem."
                            .formatted(repsMax, minRir),
                    null, latest, previous, false);
        }

        int streak = topStreak(comparable);
        if (streak < rule.sessionsAtTop()) {
            return new Analysis(trend, detail, Suggestion.CONFIRM_TOP,
                    "Teto da faixa atingido em %d de %d sessões seguidas exigidas pela sua regra. Repita a carga para confirmar."
                            .formatted(streak, rule.sessionsAtTop()),
                    null, latest, previous, false);
        }

        BigDecimal suggested = latest.topLoad().add(rule.increment());
        boolean rirMissing = latest.validSets().stream().anyMatch(set -> set.rir() == null);
        String message = "Todas as %d séries válidas chegaram a %d reps (teto da faixa %s). Avaliar aumento para %s na próxima sessão, respeitando a execução e o RIR planejado."
                .formatted(latest.validSets().size(), repsMax, range(latest), ExecutionRecord.formatLoad(suggested))
                + (rirMissing ? " (RIR não informado em todas as séries.)" : "");
        return new Analysis(trend, detail, Suggestion.INCREASE_LOAD, message, suggested, latest, previous, false);
    }

    /** Todas as séries válidas (pelo menos as planejadas) no teto da faixa. */
    static boolean reachedTop(ExecutionRecord record) {
        if (record.repsMax() == null || !record.isComplete()) {
            return false;
        }
        List<Integer> reps = record.validReps();
        return reps.size() == record.validSets().size() && reps.stream().allMatch(r -> r >= record.repsMax());
    }

    /** Quantas execuções comparáveis seguidas (das mais recentes) chegaram ao teto com a mesma carga. */
    static int topStreak(List<ExecutionRecord> comparable) {
        int streak = 0;
        BigDecimal load = null;
        for (int i = comparable.size() - 1; i >= 0; i--) {
            ExecutionRecord record = comparable.get(i);
            if (!reachedTop(record) || (load != null && record.topLoad().compareTo(load) != 0)) {
                break;
            }
            load = record.topLoad();
            streak++;
        }
        return streak;
    }

    private record Comparison(Trend trend, String detail) {
    }

    private static Comparison compare(ExecutionRecord current, ExecutionRecord previous) {
        BigDecimal currentLoad = current.topLoad();
        BigDecimal previousLoad = previous.topLoad();
        boolean rangeChanged = !Objects.equals(current.repsMin(), previous.repsMin())
                || !Objects.equals(current.repsMax(), previous.repsMax());

        if (rangeChanged) {
            // faixa de repetições mudou (nova fase): compara pelo 1RM estimado
            BigDecimal now = current.bestEstimatedOneRepMax();
            BigDecimal before = previous.bestEstimatedOneRepMax();
            if (now == null || before == null) {
                return new Comparison(Trend.STABLE, "A faixa de repetições mudou; comparação limitada.");
            }
            BigDecimal ratio = now.divide(before, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("1.02")) > 0) {
                return new Comparison(Trend.PROGRESSING, "A faixa de repetições mudou; o 1RM estimado subiu.");
            }
            if (ratio.compareTo(new BigDecimal("0.98")) < 0) {
                return new Comparison(Trend.REGRESSING, "A faixa de repetições mudou; o 1RM estimado caiu.");
            }
            return new Comparison(Trend.STABLE, "A faixa de repetições mudou; desempenho equivalente.");
        }

        int loadCompare = currentLoad.compareTo(previousLoad);
        if (loadCompare > 0) {
            return new Comparison(Trend.PROGRESSING, "Carga subiu de %s para %s.".formatted(
                    ExecutionRecord.formatLoad(previousLoad), ExecutionRecord.formatLoad(currentLoad)));
        }
        if (loadCompare < 0) {
            return new Comparison(Trend.REGRESSING, "Carga caiu de %s para %s.".formatted(
                    ExecutionRecord.formatLoad(previousLoad), ExecutionRecord.formatLoad(currentLoad)));
        }
        double before = average(previous.repsAtTopLoad());
        double now = average(current.repsAtTopLoad());
        String reps = "%s → %s".formatted(previous.summary(), current.summary());
        if (now > before + 0.001) {
            return new Comparison(Trend.PROGRESSING, "Mais repetições com a mesma carga: " + reps + ".");
        }
        if (now < before - 0.001) {
            return new Comparison(Trend.REGRESSING, "Menos repetições com a mesma carga: " + reps + ".");
        }
        return new Comparison(Trend.STABLE, "Mesmo desempenho: " + current.summary() + ".");
    }

    private static double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static String incompleteDetail(ExecutionRecord latest) {
        int valid = latest.validSets().size();
        String sets = latest.plannedSets() == null
                ? "%d série(s) válida(s)".formatted(valid)
                : "%d de %d séries".formatted(valid, latest.plannedSets());
        return "Última execução incompleta (%s); não é tratada como queda de desempenho.".formatted(sets);
    }

    private static String range(ExecutionRecord record) {
        return record.repsMin() == null || record.repsMin().equals(record.repsMax())
                ? String.valueOf(record.repsMax())
                : record.repsMin() + "–" + record.repsMax();
    }
}
