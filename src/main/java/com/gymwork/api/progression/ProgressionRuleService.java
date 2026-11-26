package com.gymwork.api.progression;

import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.progression.dto.RuleRequest;
import com.gymwork.api.progression.dto.RuleResponse;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regra de progressão por exercício (A12). Sem registro, vale a regra padrão. */
@Service
public class ProgressionRuleService {

    private final ProgressionRuleRepository ruleRepository;
    private final ExerciseService exerciseService;

    public ProgressionRuleService(ProgressionRuleRepository ruleRepository, ExerciseService exerciseService) {
        this.ruleRepository = ruleRepository;
        this.exerciseService = exerciseService;
    }

    @Transactional(readOnly = true)
    public RuleResponse get(UUID userId, UUID exerciseId) {
        exerciseService.get(userId, exerciseId);
        return response(exerciseId, ruleRepository.findByUserIdAndExerciseId(userId, exerciseId).isPresent(),
                settings(userId, exerciseId));
    }

    @Transactional
    public RuleResponse update(UUID userId, UUID exerciseId, RuleRequest request) {
        exerciseService.get(userId, exerciseId);
        ProgressionRule rule = ruleRepository.findByUserIdAndExerciseId(userId, exerciseId)
                .orElseGet(() -> ruleRepository.save(new ProgressionRule(userId, exerciseId)));
        RuleSettings settings = new RuleSettings(
                request.minRir(), request.sessionsAtTop(), request.loadIncrementKg(), request.enabled());
        rule.update(settings);
        return response(exerciseId, true, settings);
    }

    /** Volta à regra padrão. */
    @Transactional
    public RuleResponse reset(UUID userId, UUID exerciseId) {
        exerciseService.get(userId, exerciseId);
        ruleRepository.findByUserIdAndExerciseId(userId, exerciseId).ifPresent(ruleRepository::delete);
        return response(exerciseId, false, RuleSettings.DEFAULT);
    }

    RuleSettings settings(UUID userId, UUID exerciseId) {
        return ruleRepository.findByUserIdAndExerciseId(userId, exerciseId)
                .map(ProgressionRule::settings)
                .orElse(RuleSettings.DEFAULT);
    }

    Map<UUID, RuleSettings> settings(UUID userId, Collection<UUID> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        return ruleRepository.findByUserIdAndExerciseIdIn(userId, exerciseIds).stream()
                .collect(Collectors.toMap(ProgressionRule::getExerciseId, ProgressionRule::settings,
                        (a, b) -> a));
    }

    static RuleResponse response(UUID exerciseId, boolean custom, RuleSettings settings) {
        return new RuleResponse(
                exerciseId,
                custom,
                settings.minRir(),
                settings.sessionsAtTop(),
                settings.loadIncrementKg(),
                settings.increment(),
                settings.enabled());
    }
}
