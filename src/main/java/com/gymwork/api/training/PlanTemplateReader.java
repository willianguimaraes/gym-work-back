package com.gymwork.api.training;

import com.gymwork.api.common.SearchText;
import com.gymwork.api.training.dto.PlanImportPreview.Issue;
import com.gymwork.api.training.dto.PlanTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê e valida um planejamento no template {@value PlanTemplate#FORMAT} sem tocar no banco. Usa os
 * mesmos limites do cadastro manual e junta todos os problemas (com o caminho no JSON), em vez de
 * parar no primeiro, para que a pré-visualização mostre tudo de uma vez.
 *
 * <p>É tolerante com o que costuma vir do chat de uma IA: texto antes ou depois do JSON, cercas
 * {@code ```json}, números como texto ("4"), vírgula decimal ("42,5") e nomes de enums em
 * minúsculas. Dias da semana também são aceitos em português ("segunda", "sábado").
 */
final class PlanTemplateReader {

    static final int MAX_PHASES = 12;
    static final int MAX_DAYS = 14;
    static final int MAX_EXERCISES = 40;

    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,9}([.,]\\d+)?");
    private static final BigDecimal MAX_LOAD = new BigDecimal("9999.99");
    private static final Set<String> ROOT_FIELDS = Set.of("format", "plan", "phases");
    private static final Set<String> PLAN_FIELDS = Set.of("name", "goal", "notes");
    private static final Set<String> PHASE_FIELDS = Set.of("name", "weeks", "deloadWeeks", "notes", "days");
    private static final Set<String> DAY_FIELDS = Set.of("name", "dayOfWeek", "notes", "exercises");
    private static final Set<String> EXERCISE_FIELDS = Set.of(
            "exercise", "sets", "repsMin", "repsMax", "restSeconds", "targetRir", "suggestedLoadKg", "notes");
    private static final Map<String, DayOfWeek> PORTUGUESE_DAYS = Map.of(
            "segunda", DayOfWeek.MONDAY,
            "terca", DayOfWeek.TUESDAY,
            "quarta", DayOfWeek.WEDNESDAY,
            "quinta", DayOfWeek.THURSDAY,
            "sexta", DayOfWeek.FRIDAY,
            "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    /** @param template o plano lido; {@code null} quando há erros */
    record Result(PlanTemplate template, List<Issue> errors, List<Issue> warnings) {
    }

    private final List<Issue> errors = new ArrayList<>();
    private final List<Issue> warnings = new ArrayList<>();

    private PlanTemplateReader() {
    }

    /**
     * @param planRequired {@code false} quando as fases vão para um planejamento existente: o bloco
     *     {@code plan} do arquivo é então opcional (e ignorado)
     */
    static Result read(JsonMapper mapper, String content, boolean planRequired) {
        PlanTemplateReader reader = new PlanTemplateReader();
        PlanTemplate template = reader.parse(mapper, content == null ? "" : content, planRequired);
        return new Result(reader.errors.isEmpty() ? template : null, List.copyOf(reader.errors), List.copyOf(reader.warnings));
    }

    // ---------------------------------------------------------------------------- estrutura

    private PlanTemplate parse(JsonMapper mapper, String content, boolean planRequired) {
        int[] range = jsonRange(content);
        if (range == null) {
            error("", "Arquivo", "Não encontrei um JSON no texto. Ele deve começar com { e terminar com }.");
            return null;
        }
        JsonNode root;
        try {
            root = mapper.readTree(content.substring(range[0], range[1]));
        } catch (JacksonException ex) {
            String where = "";
            if (ex.getLocation() != null && ex.getLocation().getLineNr() > 0) {
                int line = ex.getLocation().getLineNr() + lineOf(content, range[0]) - 1;
                where = " perto da linha " + line + ", coluna " + ex.getLocation().getColumnNr();
            }
            error("", "Arquivo", "JSON inválido" + where + ". Confira vírgulas, aspas, chaves e colchetes.");
            return null;
        }
        if (root == null || !root.isObject()) {
            error("", "Arquivo", "O JSON deve ser um objeto com \"format\", \"plan\" e \"phases\".");
            return null;
        }
        unknownFields(root, ROOT_FIELDS, "", "Arquivo");

        JsonNode format = root.get("format");
        if (absent(format)) {
            warning("format", "Arquivo", "Sem \"format\": lido como " + PlanTemplate.FORMAT + ".");
        } else if (!format.isString() || !PlanTemplate.FORMAT.equals(format.stringValue().trim())) {
            error("format", "Arquivo", "Formato não suportado. Use \"format\": \"" + PlanTemplate.FORMAT + "\".");
        }

        PlanTemplate.Plan plan = plan(root.get("plan"), planRequired);

        JsonNode phasesNode = root.get("phases");
        List<PlanTemplate.Phase> phases = new ArrayList<>();
        if (absent(phasesNode) || !phasesNode.isArray() || phasesNode.isEmpty()) {
            error("phases", "Arquivo", "Inclua ao menos uma fase em \"phases\".");
        } else if (phasesNode.size() > MAX_PHASES) {
            error("phases", "Arquivo", "No máximo " + MAX_PHASES + " fases por importação.");
        } else {
            for (int i = 0; i < phasesNode.size(); i++) {
                PlanTemplate.Phase phase = phase(phasesNode.get(i), i);
                if (phase != null) {
                    phases.add(phase);
                }
            }
        }
        return new PlanTemplate(PlanTemplate.FORMAT, plan, phases);
    }

    private PlanTemplate.Plan plan(JsonNode node, boolean required) {
        String location = "Plano";
        if (absent(node)) {
            if (required) {
                error("plan", location, "Inclua o bloco \"plan\" com o nome e o objetivo.");
            }
            return null;
        }
        if (!node.isObject()) {
            error("plan", location, "\"plan\" deve ser um objeto.");
            return null;
        }
        unknownFields(node, PLAN_FIELDS, "plan", location);
        String name = text(node, "name", "plan", location, PlanService.NAME_MAX, required);
        PlanGoal goal = goal(node.get("goal"), required);
        String notes = text(node, "notes", "plan", location, 2000, false);
        return new PlanTemplate.Plan(name, goal, notes);
    }

    private PlanGoal goal(JsonNode node, boolean required) {
        if (absent(node)) {
            if (required) {
                error("plan.goal", "Plano", "Informe o objetivo (" + names(PlanGoal.values()) + ").");
            }
            return null;
        }
        if (node.isString()) {
            String value = node.stringValue().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            for (PlanGoal goal : PlanGoal.values()) {
                if (goal.name().equals(value)) {
                    return goal;
                }
            }
        }
        error("plan.goal", "Plano", "Objetivo inválido. Use " + names(PlanGoal.values()) + ".");
        return null;
    }

    private PlanTemplate.Phase phase(JsonNode node, int index) {
        String path = "phases[" + index + "]";
        String location = "Fase " + (index + 1) + label(node, "name");
        if (!node.isObject()) {
            error(path, location, "Cada fase deve ser um objeto.");
            return null;
        }
        unknownFields(node, PHASE_FIELDS, path, location);
        String name = text(node, "name", path, location, PlanService.NAME_MAX, true);
        Integer weeks = integer(node, "weeks", path, location, 1, 52, true);
        String notes = text(node, "notes", path, location, 2000, false);
        List<Integer> deloads = deloadWeeks(node.get("deloadWeeks"), path, location, weeks);

        JsonNode daysNode = node.get("days");
        List<PlanTemplate.Day> days = new ArrayList<>();
        if (absent(daysNode)) {
            warning(path + ".days", location, "Fase sem dias de treino: adicione depois no planejamento.");
        } else if (!daysNode.isArray()) {
            error(path + ".days", location, "\"days\" deve ser uma lista.");
        } else if (daysNode.size() > MAX_DAYS) {
            error(path + ".days", location, "No máximo " + MAX_DAYS + " dias de treino por fase.");
        } else {
            if (daysNode.isEmpty()) {
                warning(path + ".days", location, "Fase sem dias de treino: adicione depois no planejamento.");
            }
            for (int i = 0; i < daysNode.size(); i++) {
                PlanTemplate.Day day = day(daysNode.get(i), path + ".days[" + i + "]", location + " › Dia " + (i + 1), i);
                if (day != null) {
                    days.add(day);
                }
            }
        }
        if (name == null || weeks == null) {
            return null;
        }
        return new PlanTemplate.Phase(name, weeks, deloads, notes, days);
    }

    private List<Integer> deloadWeeks(JsonNode node, String path, String location, Integer weeks) {
        if (absent(node)) {
            return List.of();
        }
        String field = path + ".deloadWeeks";
        if (!node.isArray()) {
            error(field, location, "\"deloadWeeks\" deve ser uma lista com os números das semanas (ex.: [4, 8]).");
            return List.of();
        }
        Set<Integer> numbers = new LinkedHashSet<>();
        for (int i = 0; i < node.size(); i++) {
            Integer number = integerValue(node.get(i));
            if (number == null) {
                error(field + "[" + i + "]", location, "Semana de deload deve ser um número inteiro.");
            } else if (weeks != null && (number < 1 || number > weeks)) {
                error(field + "[" + i + "]", location,
                        "Semana de deload " + number + " fora da fase (1 a " + weeks + ").");
            } else if (!numbers.add(number)) {
                warning(field + "[" + i + "]", location, "Semana de deload " + number + " repetida.");
            }
        }
        return numbers.stream().sorted().toList();
    }

    private PlanTemplate.Day day(JsonNode node, String path, String parent, int index) {
        String location = parent + label(node, "name");
        if (!node.isObject()) {
            error(path, location, "Cada dia deve ser um objeto.");
            return null;
        }
        unknownFields(node, DAY_FIELDS, path, location);
        String name = text(node, "name", path, location, 60, true);
        DayOfWeek dayOfWeek = dayOfWeek(node.get("dayOfWeek"), path + ".dayOfWeek", location);
        String notes = text(node, "notes", path, location, 2000, false);

        JsonNode exercisesNode = node.get("exercises");
        List<PlanTemplate.Exercise> exercises = new ArrayList<>();
        if (absent(exercisesNode) || (exercisesNode.isArray() && exercisesNode.isEmpty())) {
            warning(path + ".exercises", location, "Dia sem exercícios.");
        } else if (!exercisesNode.isArray()) {
            error(path + ".exercises", location, "\"exercises\" deve ser uma lista.");
        } else if (exercisesNode.size() > MAX_EXERCISES) {
            error(path + ".exercises", location, "No máximo " + MAX_EXERCISES + " exercícios por dia.");
        } else {
            for (int i = 0; i < exercisesNode.size(); i++) {
                PlanTemplate.Exercise exercise =
                        exercise(exercisesNode.get(i), path + ".exercises[" + i + "]", location + " › Exercício " + (i + 1));
                if (exercise != null) {
                    exercises.add(exercise);
                }
            }
        }
        return name == null ? null : new PlanTemplate.Day(name, dayOfWeek, notes, exercises);
    }

    private DayOfWeek dayOfWeek(JsonNode node, String path, String location) {
        if (absent(node)) {
            return null;
        }
        if (node.isString()) {
            String value = SearchText.normalize(node.stringValue()).replace("-feira", "").trim();
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day.name().equalsIgnoreCase(value)) {
                    return day;
                }
            }
            DayOfWeek portuguese = PORTUGUESE_DAYS.get(value);
            if (portuguese != null) {
                return portuguese;
            }
        }
        error(path, location, "Dia da semana inválido. Use MONDAY a SUNDAY (ou null).");
        return null;
    }

    private PlanTemplate.Exercise exercise(JsonNode node, String path, String parent) {
        String location = parent + label(node, "exercise");
        if (!node.isObject()) {
            error(path, location, "Cada exercício deve ser um objeto.");
            return null;
        }
        unknownFields(node, EXERCISE_FIELDS, path, location);
        String name = text(node, "exercise", path, location, 120, true);
        Integer sets = integer(node, "sets", path, location, 1, 20, true);
        Integer repsMin = integer(node, "repsMin", path, location, 1, 100, true);
        Integer repsMax = integer(node, "repsMax", path, location, 1, 100, true);
        Integer rest = integer(node, "restSeconds", path, location, 0, 900, false);
        Integer rir = integer(node, "targetRir", path, location, 0, 10, false);
        BigDecimal load = load(node.get("suggestedLoadKg"), path + ".suggestedLoadKg", location);
        String notes = text(node, "notes", path, location, 2000, false);
        if (repsMin != null && repsMax != null && repsMin > repsMax) {
            error(path + ".repsMax", location, "repsMax deve ser maior ou igual a repsMin.");
            return null;
        }
        if (name == null || sets == null || repsMin == null || repsMax == null) {
            return null;
        }
        return new PlanTemplate.Exercise(name, sets, repsMin, repsMax, rest, rir, load, notes);
    }

    private BigDecimal load(JsonNode node, String path, String location) {
        if (absent(node)) {
            return null;
        }
        BigDecimal value = null;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isString() && DECIMAL.matcher(node.stringValue().trim()).matches()) {
            value = new BigDecimal(node.stringValue().trim().replace(',', '.'));
        }
        if (value == null) {
            error(path, location, "suggestedLoadKg deve ser um número em kg (ou null).");
            return null;
        }
        if (value.signum() < 0 || value.compareTo(MAX_LOAD) > 0) {
            error(path, location, "suggestedLoadKg deve estar entre 0 e 9999,99 kg.");
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2) {
            normalized = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
            warning(path, location, "Carga arredondada para " + normalized.toPlainString() + " kg.");
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    // ---------------------------------------------------------------------------- campos

    private String text(JsonNode parent, String field, String path, String location, int max, boolean required) {
        JsonNode node = parent.get(field);
        String fullPath = path.isEmpty() ? field : path + "." + field;
        if (absent(node) || (node.isString() && node.stringValue().isBlank())) {
            if (required) {
                error(fullPath, location, "Informe \"" + field + "\".");
            }
            return null;
        }
        if (!node.isString() && !node.isNumber()) {
            error(fullPath, location, "\"" + field + "\" deve ser um texto.");
            return null;
        }
        String raw = node.asString().trim();
        // observações mantêm as quebras de linha; nomes ficam com espaços simples
        String value = field.equals("notes") ? raw : raw.replaceAll("\\s+", " ");
        if (value.length() > max) {
            error(fullPath, location, "\"" + field + "\" pode ter no máximo " + max + " caracteres.");
            return null;
        }
        return value;
    }

    private Integer integer(JsonNode parent, String field, String path, String location, int min, int max, boolean required) {
        JsonNode node = parent.get(field);
        String fullPath = path + "." + field;
        if (absent(node)) {
            if (required) {
                error(fullPath, location, "Informe \"" + field + "\".");
            }
            return null;
        }
        Integer value = integerValue(node);
        if (value == null) {
            error(fullPath, location, "\"" + field + "\" deve ser um número inteiro.");
            return null;
        }
        if (value < min || value > max) {
            error(fullPath, location, "\"" + field + "\" deve estar entre " + min + " e " + max + ".");
            return null;
        }
        return value;
    }

    /** Inteiro, inclusive escrito como texto ("4") ou com casas zeradas (4.0). */
    private static Integer integerValue(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue().stripTrailingZeros();
            return decimal.scale() <= 0 && node.canConvertToInt() ? decimal.intValue() : null;
        }
        if (node.isString() && INTEGER.matcher(node.stringValue().trim()).matches()) {
            return Integer.parseInt(node.stringValue().trim());
        }
        return null;
    }

    // ---------------------------------------------------------------------------- apoio

    private void unknownFields(JsonNode node, Set<String> known, String path, String location) {
        for (String name : node.propertyNames()) {
            if (!known.contains(name)) {
                warning(path.isEmpty() ? name : path + "." + name, location, "Campo \"" + name + "\" ignorado.");
            }
        }
    }

    private static boolean absent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    /** " (nome)" para a localização, quando o objeto tem o campo em texto. */
    private static String label(JsonNode node, String field) {
        JsonNode value = node == null || !node.isObject() ? null : node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            return "";
        }
        String text = value.stringValue().trim();
        return " (" + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + ")";
    }

    private static String names(Enum<?>[] values) {
        List<String> names = new ArrayList<>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return String.join(", ", names);
    }

    private void error(String path, String location, String message) {
        errors.add(new Issue(path, location, message));
    }

    private void warning(String path, String location, String message) {
        warnings.add(new Issue(path, location, message));
    }

    /**
     * Trecho do texto que contém o JSON: dentro da primeira cerca {@code ```} que tenha um objeto,
     * ou do primeiro "{" ao último "}". Retorna {@code [início, fim)} ou {@code null}.
     */
    static int[] jsonRange(String content) {
        int from = 0;
        while (true) {
            int fence = content.indexOf("```", from);
            if (fence < 0) {
                break;
            }
            int bodyStart = content.indexOf('\n', fence);
            if (bodyStart < 0) {
                break;
            }
            int close = content.indexOf("```", bodyStart);
            if (close < 0) {
                break;
            }
            int[] inside = braces(content, bodyStart + 1, close);
            if (inside != null) {
                return inside;
            }
            from = close + 3;
        }
        return braces(content, 0, content.length());
    }

    private static int[] braces(String content, int start, int end) {
        int open = content.indexOf('{', start);
        int close = content.lastIndexOf('}', end - 1);
        return open < 0 || open >= end || close < open ? null : new int[] {open, close + 1};
    }

    private static int lineOf(String content, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
