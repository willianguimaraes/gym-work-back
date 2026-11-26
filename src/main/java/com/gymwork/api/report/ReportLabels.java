package com.gymwork.api.report;

import com.gymwork.api.user.WeightUnit;
import com.gymwork.api.workout.BodyRegion;
import com.gymwork.api.workout.BodySide;
import com.gymwork.api.workout.ObservationCategory;
import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Rótulos e formatação em português usados nos relatórios (mesmos textos do frontend). */
public final class ReportLabels {

    private static final BigDecimal KG_PER_LB = new BigDecimal("0.45359237");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReportLabels() {
    }

    public static String weekday(DayOfWeek day) {
        if (day == null) {
            return null;
        }
        return switch (day) {
            case MONDAY -> "SEG";
            case TUESDAY -> "TER";
            case WEDNESDAY -> "QUA";
            case THURSDAY -> "QUI";
            case FRIDAY -> "SEX";
            case SATURDAY -> "SÁB";
            case SUNDAY -> "DOM";
        };
    }

    public static String category(ObservationCategory category) {
        return switch (category) {
            case PAIN -> "Dor";
            case DISCOMFORT -> "Desconforto";
            case RECOVERY -> "Recuperação";
            case SLEEP -> "Sono";
            case ENERGY -> "Energia";
            case EXECUTION_DIFFICULTY -> "Dificuldade na execução";
            case EQUIPMENT_UNAVAILABLE -> "Equipamento indisponível";
            case GENERAL -> "Observação";
        };
    }

    public static String region(BodyRegion region) {
        return switch (region) {
            case NECK -> "Pescoço";
            case SHOULDER -> "Ombro";
            case UPPER_BACK -> "Costas (alto)";
            case LOWER_BACK -> "Lombar";
            case CHEST -> "Peito";
            case ELBOW -> "Cotovelo";
            case WRIST -> "Punho";
            case HAND -> "Mão";
            case ABDOMEN -> "Abdômen";
            case HIP -> "Quadril";
            case GLUTE -> "Glúteo";
            case THIGH -> "Coxa";
            case KNEE -> "Joelho";
            case CALF -> "Panturrilha";
            case ANKLE -> "Tornozelo";
            case FOOT -> "Pé";
            case OTHER -> "Outra região";
        };
    }

    public static String side(BodySide side) {
        return switch (side) {
            case LEFT -> "E";
            case RIGHT -> "D";
            case BOTH -> "(ambos os lados)";
        };
    }

    public static String setType(SetType type) {
        return switch (type) {
            case WARMUP -> "Aquecimento";
            case ACTIVATION -> "Ativação";
            case WORKING -> "Trabalho";
            case DROP_SET -> "Drop set";
            case REST_PAUSE -> "Rest-pause";
            case EXTRA -> "Adicional";
        };
    }

    public static String setStatus(SetStatus status) {
        return switch (status) {
            case COMPLETED -> "Concluída";
            case NOT_PERFORMED -> "Não realizada";
            case INTERRUPTED -> "Interrompida";
            case SUBSTITUTED -> "Substituída";
        };
    }

    /**
     * Uma observação em uma linha: "[Dor] Ombro D, intensidade 6, interrompeu o exercício
     * (Supino): texto". Observação geral sai só com o texto.
     */
    public static String observation(Report.Observation o) {
        List<String> details = new ArrayList<>();
        if (o.bodyRegion() != null) {
            details.add(region(o.bodyRegion()) + (o.bodySide() == null ? "" : " " + side(o.bodySide())));
        }
        if (o.intensity() != null) {
            details.add("intensidade " + o.intensity());
        }
        if (o.interrupted()) {
            details.add("interrompeu o exercício");
        }
        StringBuilder line = new StringBuilder();
        boolean general = o.category() == ObservationCategory.GENERAL;
        if (!general || !details.isEmpty() || o.exerciseName() != null) {
            line.append('[').append(category(o.category())).append(']');
        }
        if (!details.isEmpty()) {
            line.append(' ').append(String.join(", ", details));
        }
        if (o.exerciseName() != null) {
            line.append(" (").append(o.exerciseName()).append(')');
        }
        if (o.text() != null && !o.text().isBlank()) {
            String separator = line.isEmpty() ? "" : details.isEmpty() && o.exerciseName() == null ? " " : ": ";
            line.append(separator).append(o.text().trim());
        }
        return line.toString();
    }

    /** "45 kg", "22,5 kg" ou, em libras, "99,2 lb". */
    public static String load(BigDecimal kg, WeightUnit unit) {
        return number(toUnit(kg, unit)) + (unit == WeightUnit.LB ? " lb" : " kg");
    }

    public static BigDecimal toUnit(BigDecimal kg, WeightUnit unit) {
        if (unit == WeightUnit.LB) {
            return kg.divide(KG_PER_LB, 1, RoundingMode.HALF_UP);
        }
        return kg;
    }

    /** Número sem zeros à direita e com vírgula decimal. */
    public static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }
}
