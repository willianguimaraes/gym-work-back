package com.gymwork.api.training;

import com.gymwork.api.common.SearchText;
import com.gymwork.api.common.exception.InvalidFieldsException;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.training.dto.PlanImportPreview;
import com.gymwork.api.training.dto.PlanImportPreview.Candidate;
import com.gymwork.api.training.dto.PlanImportPreview.ExerciseMatch;
import com.gymwork.api.training.dto.PlanImportPreview.Issue;
import com.gymwork.api.training.dto.PlanImportPreview.MatchStatus;
import com.gymwork.api.training.dto.PlanImportRequest;
import com.gymwork.api.training.dto.PlanImportResult;
import com.gymwork.api.training.dto.PlanTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importação de planejamento no template {@value PlanTemplate#FORMAT}: valida e mostra a
 * pré-visualização, depois cria um planejamento novo em rascunho (ou adiciona as fases no fim de um
 * planejamento existente). Nunca altera fases, dias ou exercícios que já existem.
 */
@Service
public class PlanImportService {

    static final int SUGGESTIONS = 5;

    private final JsonMapper jsonMapper;
    private final ExerciseService exerciseService;
    private final TrainingLookup lookup;
    private final TrainingPlanRepository planRepository;
    private final TrainingPhaseRepository phaseRepository;
    private final WorkoutDayRepository dayRepository;
    private final PlannedExerciseRepository plannedExerciseRepository;
    private final PhaseStructure structure;

    public PlanImportService(
            JsonMapper jsonMapper,
            ExerciseService exerciseService,
            TrainingLookup lookup,
            TrainingPlanRepository planRepository,
            TrainingPhaseRepository phaseRepository,
            WorkoutDayRepository dayRepository,
            PlannedExerciseRepository plannedExerciseRepository,
            PhaseStructure structure) {
        this.jsonMapper = jsonMapper;
        this.exerciseService = exerciseService;
        this.lookup = lookup;
        this.planRepository = planRepository;
        this.phaseRepository = phaseRepository;
        this.dayRepository = dayRepository;
        this.plannedExerciseRepository = plannedExerciseRepository;
        this.structure = structure;
    }

    @Transactional(readOnly = true)
    public PlanImportPreview preview(UUID userId, PlanImportRequest request) {
        return analyze(userId, request).preview();
    }

    @Transactional
    public PlanImportResult importPlan(UUID userId, PlanImportRequest request) {
        Analysis analysis = analyze(userId, request);
        PlanImportPreview preview = analysis.preview();
        if (!preview.ready()) {
            Map<String, String> errors = new LinkedHashMap<>();
            preview.errors().forEach(issue -> errors.putIfAbsent(issue.path(), issue.location() + ": " + issue.message()));
            preview.exercises().stream()
                    .filter(match -> !match.resolved())
                    .forEach(match -> errors.putIfAbsent(
                            "exercises." + match.name(), "Escolha um exercício do catálogo para \"" + match.name() + "\""));
            throw new InvalidFieldsException("A importação tem problemas: valide de novo para ver os detalhes", errors);
        }
        return create(userId, analysis.target(), preview.template(), analysis.resolved());
    }

    // ---------------------------------------------------------------------------- validação

    private record Analysis(PlanImportPreview preview, TrainingPlan target, Map<String, Exercise> resolved) {
    }

    private Analysis analyze(UUID userId, PlanImportRequest request) {
        TrainingPlan target = request.planId() == null ? null : lookup.plan(userId, request.planId());
        PlanTemplateReader.Result read = PlanTemplateReader.read(jsonMapper, request.content(), target == null);
        List<Issue> errors = new ArrayList<>(read.errors());
        List<Issue> warnings = new ArrayList<>(read.warnings());
        PlanTemplate template = read.template();
        if (target != null && template != null && template.plan() != null) {
            warnings.add(new Issue("plan", "Plano",
                    "As fases serão adicionadas ao fim de \"" + target.getName() + "\"; o nome e o objetivo do arquivo são ignorados."));
        }

        List<ExerciseMatch> matches = new ArrayList<>();
        Map<String, Exercise> resolved = new HashMap<>();
        if (template != null) {
            List<Exercise> catalog = exerciseService.usableCatalog(userId);
            Map<UUID, Exercise> usable = catalog.stream().collect(Collectors.toMap(Exercise::getId, Function.identity()));
            ExerciseMatcher matcher = new ExerciseMatcher(catalog);
            Map<String, UUID> chosen = new HashMap<>();
            Objects.requireNonNullElse(request.exercises(), Map.<String, UUID>of())
                    .forEach((name, id) -> {
                        if (name != null && id != null) {
                            chosen.put(SearchText.normalize(name), id);
                        }
                    });

            for (Map.Entry<String, NameUse> entry : names(template).entrySet()) {
                String key = entry.getKey();
                String name = entry.getValue().name();
                int uses = entry.getValue().uses();
                UUID chosenId = chosen.get(key);
                if (chosenId != null && usable.containsKey(chosenId)) {
                    Exercise exercise = usable.get(chosenId);
                    resolved.put(key, exercise);
                    matches.add(new ExerciseMatch(name, MatchStatus.CHOSEN, Candidate.from(exercise), List.of(), uses));
                    continue;
                }
                if (chosenId != null) {
                    warnings.add(new Issue("exercises." + name, "Exercício \"" + name + "\"",
                            "O exercício escolhido não está mais disponível no catálogo: escolha de novo."));
                }
                List<Exercise> same = matcher.sameName(name);
                if (same.size() == 1) {
                    resolved.put(key, same.get(0));
                    matches.add(new ExerciseMatch(name, MatchStatus.MATCHED, Candidate.from(same.get(0)), List.of(), uses));
                } else if (same.size() > 1) {
                    matches.add(new ExerciseMatch(name, MatchStatus.AMBIGUOUS, null, candidates(same), uses));
                } else {
                    matches.add(new ExerciseMatch(
                            name, MatchStatus.NOT_FOUND, null, candidates(matcher.similar(name, SUGGESTIONS)), uses));
                }
            }
        }

        boolean ready = template != null && errors.isEmpty() && matches.stream().allMatch(ExerciseMatch::resolved);
        PlanImportPreview.TargetPlan targetPlan = target == null
                ? null
                : new PlanImportPreview.TargetPlan(
                        target.getId(), target.getName(), phaseRepository.findByPlan(target.getId(), userId).size());
        return new Analysis(
                new PlanImportPreview(ready, List.copyOf(errors), List.copyOf(warnings), template, matches, targetPlan),
                target,
                resolved);
    }

    private record NameUse(String name, int uses) {
    }

    /** Nomes distintos (sem acento/maiúsculas), na ordem em que aparecem, com quantas vezes são usados. */
    private static Map<String, NameUse> names(PlanTemplate template) {
        Map<String, NameUse> names = new LinkedHashMap<>();
        for (PlanTemplate.Phase phase : template.phases()) {
            for (PlanTemplate.Day day : phase.days()) {
                for (PlanTemplate.Exercise exercise : day.exercises()) {
                    names.merge(
                            SearchText.normalize(exercise.exercise()),
                            new NameUse(exercise.exercise(), 1),
                            (previous, next) -> new NameUse(previous.name(), previous.uses() + 1));
                }
            }
        }
        return names;
    }

    private static List<Candidate> candidates(List<Exercise> exercises) {
        return exercises.stream().map(Candidate::from).toList();
    }

    // ---------------------------------------------------------------------------- gravação

    private PlanImportResult create(
            UUID userId, TrainingPlan target, PlanTemplate template, Map<String, Exercise> exercises) {
        TrainingPlan plan = target;
        int position = 0;
        if (plan == null) {
            plan = new TrainingPlan(userId);
            plan.update(template.plan().name(), template.plan().goal(), null, null, template.plan().notes());
            planRepository.save(plan);
        } else {
            position = phaseRepository.maxPosition(plan.getId()) + 1;
        }

        List<UUID> phaseIds = new ArrayList<>();
        for (PlanTemplate.Phase phaseTemplate : template.phases()) {
            TrainingPhase phase = new TrainingPhase(plan, position++);
            phase.update(phaseTemplate.name(), null, phaseTemplate.weeks(), phaseTemplate.notes());
            phaseRepository.save(phase);
            HashSet<Integer> deloads = new HashSet<>(phaseTemplate.deloadWeeks());
            for (TrainingWeek week : structure.syncWeeks(phase)) {
                if (deloads.contains(week.getWeekNumber())) {
                    week.update(true, null);
                }
            }
            int dayPosition = 0;
            for (PlanTemplate.Day dayTemplate : phaseTemplate.days()) {
                WorkoutDay day = new WorkoutDay(phase, dayPosition++);
                day.update(dayTemplate.name(), dayTemplate.dayOfWeek(), dayTemplate.notes());
                dayRepository.save(day);
                int exercisePosition = 0;
                for (PlanTemplate.Exercise item : dayTemplate.exercises()) {
                    PlannedExercise planned = new PlannedExercise(
                            day, exercises.get(SearchText.normalize(item.exercise())), exercisePosition++);
                    planned.configure(new PlannedExerciseConfig(
                            item.sets(),
                            item.repsMin(),
                            item.repsMax(),
                            item.restSeconds(),
                            item.suggestedLoadKg(),
                            item.targetRir(),
                            item.notes()));
                    plannedExerciseRepository.save(planned);
                }
            }
            phaseIds.add(phase.getId());
        }
        return new PlanImportResult(plan.getId(), phaseIds);
    }
}
