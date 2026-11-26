package com.gymwork.api.report;

import com.gymwork.api.assessment.AssessmentService;
import com.gymwork.api.assessment.MeasurementType;
import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.exercise.dto.MuscleGroupResponse;
import com.gymwork.api.progression.ScopeAnalysisService.ExerciseChange;
import com.gymwork.api.report.ReportLoader.Loaded;
import com.gymwork.api.report.dto.AiPackage;
import com.gymwork.api.report.dto.AnalysisReport;
import com.gymwork.api.training.PlanGoal;
import com.gymwork.api.training.PlanTemplateService;
import com.gymwork.api.training.TrainingReader;
import com.gymwork.api.training.TrainingReader.PhaseDetails;
import com.gymwork.api.training.dto.PlanTemplate;
import com.gymwork.api.user.WeightUnit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pacote para IA de uma fase (seção 11 da arquitetura): instrução, contexto, plano atual no
 * template, execução semana a semana, análise, desconfortos, avaliações físicas e o catálogo de
 * exercícios disponíveis. Sai em Markdown (para colar no chat) ou JSON (mesmos dados).
 */
@Service
public class AiPackageService {

    /** Avaliação de referência: considera também as feitas até 14 dias antes do início da fase. */
    private static final int BASELINE_DAYS = 14;

    static final List<AiPackage.TemplateField> TEMPLATE_FIELDS = List.of(
            field("format", "Sempre \"" + PlanTemplate.FORMAT + "\"."),
            field("plan.name", "Nome do planejamento (até 120 caracteres)."),
            field("plan.goal", "HYPERTROPHY, STRENGTH, FAT_LOSS, MAINTENANCE ou OTHER."),
            field("plan.notes", "Observações gerais do plano (opcional, até 2000 caracteres)."),
            field("phases[].name", "Nome da fase (até 120 caracteres), ex.: \"Fase 5 — Volume\"."),
            field("phases[].weeks", "Duração em semanas, de 1 a 52."),
            field("phases[].deloadWeeks", "Números das semanas de deload (entre 1 e weeks); [] se não houver."),
            field("phases[].notes", "Objetivo e orientações da fase (opcional)."),
            field("phases[].days[].name", "Nome do dia (até 60 caracteres), ex.: \"UPPER\", \"LOWER A\"."),
            field("phases[].days[].dayOfWeek", "MONDAY a SUNDAY, ou null se o dia não for fixo."),
            field("phases[].days[].notes", "Orientações do dia (opcional)."),
            field("phases[].days[].exercises[].exercise",
                    "Nome do exercício exatamente como no catálogo (seção 8); nomes novos serão revisados na importação."),
            field("phases[].days[].exercises[].sets", "Séries de trabalho, de 1 a 20."),
            field("phases[].days[].exercises[].repsMin", "Mínimo da faixa de repetições, de 1 a repsMax."),
            field("phases[].days[].exercises[].repsMax", "Máximo da faixa, até 100 (igual a repsMin para reps fixas)."),
            field("phases[].days[].exercises[].restSeconds", "Descanso em segundos, de 0 a 900, ou null."),
            field("phases[].days[].exercises[].targetRir", "RIR alvo, de 0 a 10, ou null."),
            field("phases[].days[].exercises[].suggestedLoadKg", "Carga sugerida em kg (≥ 0), ou null."),
            field("phases[].days[].exercises[].notes", "Técnica, cadência ou observação do exercício (opcional)."));

    static final String INSTRUCTIONS = """
            Você é um treinador experiente em musculação. Abaixo estão os dados reais de uma fase de \
            treino registrada no app Gym Work: o planejamento, a execução semana a semana, um resumo de \
            análise, os desconfortos relatados e as avaliações físicas.

            Faça duas coisas:
            1. Uma avaliação objetiva da fase: o que evoluiu, o que estagnou ou caiu (e possíveis \
            motivos), frequência e aderência, volume por grupo muscular, desconfortos que pedem atenção \
            e a evolução corporal.
            2. O planejamento da próxima fase, em um único bloco de código JSON no formato \
            "gymwork.plan/v1" (mesma estrutura do plano atual na seção 3 e campos descritos lá).

            Regras para o JSON:
            - Use os nomes dos exercícios exatamente como aparecem no catálogo (seção 8). Se precisar \
            de um exercício que não está lá, use um nome claro; ele será revisado na importação.
            - Cargas em kg. Respeite os limites dos campos. Use null quando não houver valor.
            - Baseie a progressão nas cargas e repetições registradas; não invente dados que não estão \
            no pacote.
            - Desconfortos são relatos do usuário, não diagnósticos: adapte exercícios e cargas com \
            cautela e recomende avaliação profissional quando houver dor recorrente.
            """;

    private final ReportLoader reportLoader;
    private final ReportAnalysisService analysisService;
    private final PlanTemplateService templateService;
    private final TrainingReader trainingReader;
    private final AssessmentService assessmentService;
    private final ExerciseService exerciseService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AiPackageService(
            ReportLoader reportLoader,
            ReportAnalysisService analysisService,
            PlanTemplateService templateService,
            TrainingReader trainingReader,
            AssessmentService assessmentService,
            ExerciseService exerciseService,
            JsonMapper jsonMapper,
            Clock clock) {
        this.reportLoader = reportLoader;
        this.analysisService = analysisService;
        this.templateService = templateService;
        this.trainingReader = trainingReader;
        this.assessmentService = assessmentService;
        this.exerciseService = exerciseService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiPackage build(UUID userId, UUID phaseId) {
        PhaseDetails phase = trainingReader.phaseDetails(userId, phaseId);
        Loaded loaded = reportLoader.phase(userId, phaseId);
        Report report = loaded.report();
        AnalysisReport analysis = analysisService.analyze(userId, loaded);
        PlanTemplate template = templateService.phase(userId, phaseId);
        List<TrendPoint> assessments = report.from() == null
                ? List.of()
                : assessmentService.between(userId, report.from().minusDays(BASELINE_DAYS), report.to());

        Map<String, String> muscleNames = muscleNames();
        List<String> muscleOrder = List.copyOf(muscleNames.keySet());
        List<AiPackage.CatalogItem> catalog = exerciseService.usableCatalog(userId).stream()
                .sorted(Comparator.comparingInt(
                        (Exercise e) -> position(muscleOrder, e.getPrimaryMuscleGroup())))
                .map(exercise -> new AiPackage.CatalogItem(
                        exercise.getName(),
                        muscleNames.getOrDefault(exercise.getPrimaryMuscleGroup(), exercise.getPrimaryMuscleGroup()),
                        exercise.getEquipment().name()))
                .toList();

        AiPackage.Context context = new AiPackage.Context(
                phase.planName(), phase.planGoal(), phase.phase().name(), phase.phase().weeksCount(),
                phase.phase().startDate(), phase.phase().endDate(), report.from(), report.to(), "kg", "cm");
        return new AiPackage(
                AiPackage.FORMAT,
                Instant.now(clock),
                INSTRUCTIONS,
                context,
                template,
                TEMPLATE_FIELDS,
                ReportText.text(report, WeightUnit.KG),
                analysis,
                assessments,
                catalog);
    }

    public String json(AiPackage aiPackage) {
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aiPackage);
    }

    /** Markdown para colar no chat da IA. */
    public String markdown(AiPackage p) {
        AiPackage.Context c = p.context();
        StringBuilder md = new StringBuilder();
        md.append("# Pacote de acompanhamento — ").append(c.phaseName()).append("\n\n");
        md.append("Plano: ").append(c.planName()).append(" · Gerado pelo Gym Work em ")
                .append(ReportLabels.date(LocalDate.ofInstant(p.generatedAt(), clock.getZone())))
                .append(" · Unidades: kg e cm\n\n");

        md.append("## 1. Instruções\n\n").append(p.instructions().strip()).append("\n\n");

        md.append("## 2. Contexto\n\n");
        md.append("- Objetivo do plano: ").append(goal(c.goal())).append('\n');
        md.append("- Fase: ").append(c.phaseName()).append(", ").append(c.phaseWeeks()).append(" semanas");
        if (c.phaseStart() != null) {
            md.append(", de ").append(ReportLabels.date(c.phaseStart())).append(" a ").append(ReportLabels.date(c.phaseEnd()));
        }
        md.append('\n');
        if (c.periodFrom() != null) {
            md.append("- Período com dados: ").append(ReportLabels.date(c.periodFrom())).append(" a ")
                    .append(ReportLabels.date(c.periodTo())).append('\n');
        }
        AnalysisReport.Frequency f = p.analysis().frequency();
        md.append("- Sessões finalizadas: ").append(f.completedSessions());
        if (f.plannedSessions() != null) {
            md.append(" de ").append(f.plannedSessions()).append(" planejadas");
        }
        if (f.sessionsPerWeek() != null) {
            md.append(" (").append(ReportLabels.number(f.sessionsPerWeek())).append(" por semana)");
        }
        md.append("\n\n");

        md.append("## 3. Planejamento atual (template ").append(PlanTemplate.FORMAT).append(")\n\n");
        md.append("```json\n").append(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(p.currentPlan()))
                .append("\n```\n\n");
        md.append("Campos do template:\n\n| Campo | Descrição |\n| --- | --- |\n");
        for (AiPackage.TemplateField field : p.templateFields()) {
            md.append("| `").append(field.field()).append("` | ").append(field.description()).append(" |\n");
        }
        md.append('\n');

        md.append("## 4. Execução semana a semana\n\n");
        md.append("Formato: `exercício: carga | repetições por série`; `x` = série não feita; `8+4` = série com drop set ")
                .append("ou rest-pause; `A → B` = A substituído por B.\n\n");
        md.append("```text\n").append(p.executionText().stripTrailing()).append("\n```\n\n");

        md.append("## 5. Análise\n\n").append(analysisMarkdown(p.analysis())).append('\n');

        md.append("## 6. Desconfortos e observações\n\n");
        list(md, "Desconfortos e dores", p.analysis().discomforts(), "Nenhum registrado.");
        list(md, "Observações", p.analysis().observations(), "Nenhuma.");

        md.append("## 7. Avaliações físicas\n\n").append(assessmentsMarkdown(p.assessments())).append('\n');

        md.append("## 8. Exercícios disponíveis no catálogo\n\n");
        Map<String, List<String>> byGroup = p.catalog().stream().collect(Collectors.groupingBy(
                AiPackage.CatalogItem::muscleGroup, LinkedHashMap::new,
                Collectors.mapping(AiPackage.CatalogItem::name, Collectors.toList())));
        byGroup.forEach((group, names) -> md.append("- **").append(group).append("**: ")
                .append(String.join("; ", names)).append('\n'));
        return md.toString();
    }

    /** Resumo de análise em Markdown (também usado fora do pacote). */
    static String analysisMarkdown(AnalysisReport a) {
        StringBuilder md = new StringBuilder();
        changes(md, "Evoluíram", a.progressed());
        changes(md, "Mantiveram", a.maintained());
        changes(md, "Caíram", a.regressed());
        changes(md, "Sem comparação (execução incompleta ou poucos dados)", a.inconclusive());
        list(md, "Alterações de carga", a.loadChanges(), "Nenhuma.");
        list(md, "Alterações de repetições (mesma carga)", a.repChanges(), "Nenhuma.");
        List<String> missed = a.notPerformed().stream()
                .map(m -> m.when() + (m.exercise() == null ? "" : " · " + m.exercise()) + ": " + m.detail())
                .toList();
        list(md, "Não realizado", missed, "Nada.");
        List<String> volume = a.volume().stream()
                .map(v -> v.sets() == 0
                        ? v.name() + ": " + v.secondarySets() + " séries só como secundário"
                        : v.name() + ": " + v.sets() + " séries"
                                + (v.secondarySets() > 0 ? " (+" + v.secondarySets() + " como secundário)" : ""))
                .toList();
        md.append("**Volume por grupo muscular** (séries válidas; total ").append(a.validSets())
                .append(" séries, ").append(ReportLabels.number(a.volumeKg().setScale(0, java.math.RoundingMode.HALF_UP)))
                .append(" kg de volume):\n\n");
        if (volume.isEmpty()) {
            md.append("- Sem séries válidas.\n");
        }
        volume.forEach(v -> md.append("- ").append(v).append('\n'));
        md.append('\n');
        return md.toString();
    }

    private static void changes(StringBuilder md, String title, List<ExerciseChange> changes) {
        md.append("**").append(title).append("**:\n\n");
        if (changes.isEmpty()) {
            md.append("- Nenhum.\n\n");
            return;
        }
        for (ExerciseChange change : changes) {
            md.append("- ").append(change.exerciseName()).append(": ");
            if (change.baselineSummary() != null && change.latestSummary() != null) {
                md.append(change.baselineSummary()).append(" → ").append(change.latestSummary());
            } else if (change.latestSummary() != null) {
                md.append(change.latestSummary());
            }
            String detail = change.analysis().trendDetail();
            if (detail != null && !detail.isBlank()) {
                md.append(" — ").append(detail);
            }
            md.append('\n');
        }
        md.append('\n');
    }

    private static void list(StringBuilder md, String title, List<String> items, String empty) {
        md.append("**").append(title).append("**:\n\n");
        if (items.isEmpty()) {
            md.append("- ").append(empty).append('\n');
        }
        items.forEach(item -> md.append("- ").append(item).append('\n'));
        md.append('\n');
    }

    /** Tabela das avaliações (só as colunas com algum valor) e a diferença entre a primeira e a última. */
    static String assessmentsMarkdown(List<TrendPoint> points) {
        if (points.isEmpty()) {
            return "Nenhuma avaliação no período.\n";
        }
        List<String> headers = new ArrayList<>(List.of("Data"));
        boolean weight = points.stream().anyMatch(p -> p.weightKg() != null);
        boolean fat = points.stream().anyMatch(p -> p.bodyFatPct() != null);
        List<MeasurementType> types = java.util.Arrays.stream(MeasurementType.values())
                .filter(t -> points.stream().anyMatch(p -> p.measurements().get(t) != null))
                .toList();
        if (weight) {
            headers.add("Peso (kg)");
        }
        if (fat) {
            headers.add("Gordura (%)");
        }
        types.forEach(t -> headers.add(measurement(t) + " (cm)"));
        StringBuilder md = new StringBuilder();
        md.append("| ").append(String.join(" | ", headers)).append(" |\n");
        md.append("|").append(" --- |".repeat(headers.size())).append('\n');
        for (TrendPoint p : points) {
            List<String> cells = new ArrayList<>(List.of(ReportLabels.date(p.assessedOn())));
            if (weight) {
                cells.add(value(p.weightKg()));
            }
            if (fat) {
                cells.add(value(p.bodyFatPct()));
            }
            types.forEach(t -> cells.add(value(p.measurements().get(t))));
            md.append("| ").append(String.join(" | ", cells)).append(" |\n");
        }
        if (points.size() >= 2) {
            TrendPoint first = points.get(0);
            TrendPoint last = points.get(points.size() - 1);
            List<String> diffs = new ArrayList<>();
            diff(diffs, "Peso", first.weightKg(), last.weightKg(), " kg");
            diff(diffs, "Gordura", first.bodyFatPct(), last.bodyFatPct(), " p.p.");
            for (MeasurementType t : types) {
                diff(diffs, measurement(t), first.measurements().get(t), last.measurements().get(t), " cm");
            }
            md.append("\nDiferença de ").append(ReportLabels.date(first.assessedOn())).append(" a ")
                    .append(ReportLabels.date(last.assessedOn())).append(" (só quando o valor existe nas duas): ")
                    .append(diffs.isEmpty() ? "sem valores em comum." : String.join("; ", diffs) + ".").append('\n');
        }
        return md.toString();
    }

    private static void diff(List<String> diffs, String label, BigDecimal from, BigDecimal to, String unit) {
        if (from == null || to == null) {
            return;
        }
        BigDecimal d = to.subtract(from);
        diffs.add(label + " " + (d.signum() > 0 ? "+" : "") + ReportLabels.number(d) + unit);
    }

    private static String value(BigDecimal value) {
        return value == null ? "—" : ReportLabels.number(value);
    }

    static String measurement(MeasurementType type) {
        return switch (type) {
            case SHOULDERS -> "Ombros";
            case CHEST -> "Peitoral";
            case WAIST -> "Cintura";
            case ABDOMEN -> "Abdômen";
            case ARM_RIGHT -> "Braço D";
            case ARM_LEFT -> "Braço E";
            case FOREARM_RIGHT -> "Antebraço D";
            case FOREARM_LEFT -> "Antebraço E";
            case THIGH_RIGHT -> "Coxa D";
            case THIGH_LEFT -> "Coxa E";
            case CALF_RIGHT -> "Panturrilha D";
            case CALF_LEFT -> "Panturrilha E";
        };
    }

    private static String goal(PlanGoal goal) {
        return switch (goal) {
            case HYPERTROPHY -> "hipertrofia";
            case STRENGTH -> "força";
            case FAT_LOSS -> "perda de gordura";
            case MAINTENANCE -> "manutenção";
            case OTHER -> "outro";
        };
    }

    /** Nomes dos grupos musculares na ordem do app (a mesma das telas). */
    private Map<String, String> muscleNames() {
        return exerciseService.muscleGroups().stream()
                .collect(Collectors.toMap(
                        MuscleGroupResponse::code, MuscleGroupResponse::name, (a, b) -> a, LinkedHashMap::new));
    }

    private static int position(List<String> order, String code) {
        int index = order.indexOf(code);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static AiPackage.TemplateField field(String field, String description) {
        return new AiPackage.TemplateField(field, description);
    }
}
