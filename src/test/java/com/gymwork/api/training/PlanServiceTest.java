package com.gymwork.api.training;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.training.dto.PlanRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private TrainingPlanRepository planRepository;

    @Mock
    private TrainingPhaseRepository phaseRepository;

    @Mock
    private TrainingLookup lookup;

    @Mock
    private TrainingViews views;

    @Mock
    private PhaseStructure structure;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PlanService service;

    @Test
    void activatingCompletesThePreviousActivePlanAndFlushesBeforeActivating() {
        TrainingPlan previous = plan(PlanStatus.ACTIVE);
        TrainingPlan next = plan(PlanStatus.DRAFT);
        when(lookup.plan(USER, next.getId())).thenReturn(next);
        when(planRepository.findByUserIdAndStatus(USER, PlanStatus.ACTIVE)).thenReturn(Optional.of(previous));

        service.activate(USER, next.getId());

        assertThat(previous.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(next.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        verify(entityManager).flush();
    }

    @Test
    void activatingTheActivePlanChangesNothing() {
        TrainingPlan active = plan(PlanStatus.ACTIVE);
        when(lookup.plan(USER, active.getId())).thenReturn(active);

        service.activate(USER, active.getId());

        assertThat(active.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        verify(planRepository, never()).findByUserIdAndStatus(USER, PlanStatus.ACTIVE);
    }

    @Test
    void endDateCannotBeBeforeStartDate() {
        PlanRequest request = new PlanRequest(
                "Força", PlanGoal.STRENGTH, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30), null, null);

        assertThatThrownBy(() -> service.create(USER, request))
                .isInstanceOf(InvalidFieldException.class)
                .extracting("field").isEqualTo("expectedEndDate");
        verify(planRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsAsDraftAndTrimsTexts() {
        PlanRequest request = new PlanRequest("  Hipertrofia  ", PlanGoal.HYPERTROPHY, null, null, null, "   ");
        when(planRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        service.create(USER, request);

        org.mockito.ArgumentCaptor<TrainingPlan> saved = org.mockito.ArgumentCaptor.forClass(TrainingPlan.class);
        InOrder order = inOrder(planRepository, views);
        order.verify(planRepository).save(saved.capture());
        order.verify(views).plan(saved.getValue());
        assertThat(saved.getValue().getName()).isEqualTo("Hipertrofia");
        assertThat(saved.getValue().getNotes()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
    }

    @Test
    void copyNameKeepsTheFieldLimit() {
        assertThat(PhaseStructure.copyName("Fase 1", 120)).isEqualTo("Fase 1 (cópia)");
        String longName = "x".repeat(120);
        assertThat(PhaseStructure.copyName(longName, 120)).hasSize(120).endsWith(" (cópia)");
    }

    private static TrainingPlan plan(PlanStatus status) {
        TrainingPlan plan = withId(new TrainingPlan(USER));
        plan.update("Plano", PlanGoal.HYPERTROPHY, null, null, null);
        plan.changeStatus(status);
        return plan;
    }
}
