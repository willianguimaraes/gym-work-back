package com.gymwork.api.training;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.training.dto.PlanRequest;
import com.gymwork.api.training.dto.PlanResponse;
import com.gymwork.api.training.dto.PlanSummaryResponse;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Planejamentos do usuário: CRUD, ativação (um ativo por vez) e duplicação. */
@Service
public class PlanService {

    static final int NAME_MAX = 120;

    private final TrainingPlanRepository planRepository;
    private final TrainingPhaseRepository phaseRepository;
    private final TrainingLookup lookup;
    private final TrainingViews views;
    private final PhaseStructure structure;
    private final EntityManager entityManager;

    public PlanService(
            TrainingPlanRepository planRepository,
            TrainingPhaseRepository phaseRepository,
            TrainingLookup lookup,
            TrainingViews views,
            PhaseStructure structure,
            EntityManager entityManager) {
        this.planRepository = planRepository;
        this.phaseRepository = phaseRepository;
        this.lookup = lookup;
        this.views = views;
        this.structure = structure;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> list(UUID userId) {
        Map<UUID, long[]> totals = new HashMap<>();
        for (Object[] row : phaseRepository.totalsByPlan(userId)) {
            totals.put((UUID) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return planRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(plan -> {
                    long[] total = totals.getOrDefault(plan.getId(), new long[] {0, 0});
                    return new PlanSummaryResponse(
                            plan.getId(),
                            plan.getName(),
                            plan.getGoal(),
                            plan.getStatus(),
                            plan.getStartDate(),
                            plan.getExpectedEndDate(),
                            (int) total[0],
                            (int) total[1],
                            plan.getUpdatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID userId, UUID planId) {
        return views.plan(lookup.plan(userId, planId));
    }

    @Transactional
    public PlanResponse create(UUID userId, PlanRequest request) {
        TrainingPlan plan = new TrainingPlan(userId);
        apply(plan, request);
        planRepository.save(plan);
        if (request.status() != null) {
            changeStatus(plan, request.status());
        }
        return views.plan(plan);
    }

    @Transactional
    public PlanResponse update(UUID userId, UUID planId, PlanRequest request) {
        TrainingPlan plan = lookup.plan(userId, planId);
        apply(plan, request);
        if (request.status() != null) {
            changeStatus(plan, request.status());
        }
        return views.plan(plan);
    }

    @Transactional
    public void delete(UUID userId, UUID planId) {
        planRepository.delete(lookup.plan(userId, planId));
    }

    /** Torna o planejamento ativo; o que estava ativo passa a COMPLETED. */
    @Transactional
    public PlanResponse activate(UUID userId, UUID planId) {
        TrainingPlan plan = lookup.plan(userId, planId);
        changeStatus(plan, PlanStatus.ACTIVE);
        return views.plan(plan);
    }

    /** Cria um rascunho com as mesmas fases, semanas (deload), dias e exercícios. Sem datas. */
    @Transactional
    public PlanResponse duplicate(UUID userId, UUID planId) {
        TrainingPlan source = lookup.plan(userId, planId);
        TrainingPlan copy = new TrainingPlan(userId);
        copy.update(
                PhaseStructure.copyName(source.getName(), NAME_MAX),
                source.getGoal(), null, null, source.getNotes());
        planRepository.save(copy);
        for (TrainingPhase phase : phaseRepository.findByPlan(source.getId(), userId)) {
            TrainingPhase phaseCopy = new TrainingPhase(copy, phase.getPosition());
            phaseCopy.update(phase.getName(), null, phase.getWeeksCount(), phase.getNotes());
            phaseRepository.save(phaseCopy);
            structure.syncWeeks(phaseCopy);
            structure.copyContents(phase, phaseCopy);
        }
        return views.plan(copy);
    }

    private void changeStatus(TrainingPlan plan, PlanStatus status) {
        if (plan.getStatus() == status) {
            return;
        }
        if (status == PlanStatus.ACTIVE) {
            planRepository.findByUserIdAndStatus(plan.getUserId(), PlanStatus.ACTIVE).ifPresent(previous -> {
                previous.changeStatus(PlanStatus.COMPLETED);
                // grava antes de ativar o novo, por causa do índice "um ativo por usuário"
                entityManager.flush();
            });
        }
        plan.changeStatus(status);
    }

    private static void apply(TrainingPlan plan, PlanRequest request) {
        if (request.startDate() != null
                && request.expectedEndDate() != null
                && request.expectedEndDate().isBefore(request.startDate())) {
            throw new InvalidFieldException("expectedEndDate", "A data de término deve ser igual ou posterior à de início");
        }
        plan.update(
                request.name().trim(),
                request.goal(),
                request.startDate(),
                request.expectedEndDate(),
                TrainingText.blankToNull(request.notes()));
    }
}
