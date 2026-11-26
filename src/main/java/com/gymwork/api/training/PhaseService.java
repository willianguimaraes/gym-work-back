package com.gymwork.api.training;

import com.gymwork.api.training.dto.PhaseRequest;
import com.gymwork.api.training.dto.PhaseResponse;
import com.gymwork.api.training.dto.WeekRequest;
import com.gymwork.api.training.dto.WeekResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fases do planejamento e suas semanas. */
@Service
public class PhaseService {

    private final TrainingPhaseRepository phaseRepository;
    private final TrainingLookup lookup;
    private final TrainingViews views;
    private final PhaseStructure structure;

    public PhaseService(
            TrainingPhaseRepository phaseRepository,
            TrainingLookup lookup,
            TrainingViews views,
            PhaseStructure structure) {
        this.phaseRepository = phaseRepository;
        this.lookup = lookup;
        this.views = views;
        this.structure = structure;
    }

    /** Cria a fase no fim do planejamento e gera as semanas 1..N. */
    @Transactional
    public PhaseResponse create(UUID userId, UUID planId, PhaseRequest request) {
        TrainingPlan plan = lookup.plan(userId, planId);
        TrainingPhase phase = new TrainingPhase(plan, phaseRepository.maxPosition(plan.getId()) + 1);
        apply(phase, request);
        phaseRepository.save(phase);
        structure.syncWeeks(phase);
        return views.phase(phase);
    }

    @Transactional(readOnly = true)
    public PhaseResponse get(UUID userId, UUID phaseId) {
        return views.phase(lookup.phase(userId, phaseId));
    }

    @Transactional
    public PhaseResponse update(UUID userId, UUID phaseId, PhaseRequest request) {
        TrainingPhase phase = lookup.phase(userId, phaseId);
        apply(phase, request);
        structure.syncWeeks(phase);
        return views.phase(phase);
    }

    @Transactional
    public void delete(UUID userId, UUID phaseId) {
        phaseRepository.delete(lookup.phase(userId, phaseId));
    }

    /** Cria uma nova fase no fim do mesmo planejamento, com os mesmos dias e exercícios. */
    @Transactional
    public PhaseResponse duplicate(UUID userId, UUID phaseId) {
        TrainingPhase source = lookup.phase(userId, phaseId);
        TrainingPlan plan = source.getPlan();
        TrainingPhase copy = new TrainingPhase(plan, phaseRepository.maxPosition(plan.getId()) + 1);
        copy.update(
                PhaseStructure.copyName(source.getName(), PlanService.NAME_MAX),
                null, source.getWeeksCount(), source.getNotes());
        phaseRepository.save(copy);
        structure.syncWeeks(copy);
        structure.copyContents(source, copy);
        return views.phase(copy);
    }

    @Transactional
    public WeekResponse updateWeek(UUID userId, UUID weekId, WeekRequest request) {
        TrainingWeek week = lookup.week(userId, weekId);
        week.update(request.deload(), TrainingText.blankToNull(request.notes()));
        return WeekResponse.from(week);
    }

    private static void apply(TrainingPhase phase, PhaseRequest request) {
        phase.update(
                request.name().trim(),
                request.startDate(),
                request.weeksCount(),
                TrainingText.blankToNull(request.notes()));
    }
}
