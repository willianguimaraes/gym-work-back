package com.gymwork.api.training;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.exception.InvalidFieldException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhaseStructureTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private TrainingWeekRepository weekRepository;

    @Mock
    private WorkoutDayRepository dayRepository;

    @Mock
    private PlannedExerciseRepository plannedExerciseRepository;

    @InjectMocks
    private PhaseStructure structure;

    @Test
    void createsMissingWeeksWithStartDates() {
        TrainingPhase phase = phase(LocalDate.of(2026, 1, 5), 3);
        when(weekRepository.findByPhase(phase.getId(), USER)).thenReturn(List.of(new TrainingWeek(phase, 1)));

        structure.syncWeeks(phase);

        ArgumentCaptor<TrainingWeek> created = ArgumentCaptor.forClass(TrainingWeek.class);
        verify(weekRepository, times(2)).save(created.capture());
        assertThat(created.getAllValues()).extracting(TrainingWeek::getWeekNumber).containsExactly(2, 3);
        assertThat(created.getAllValues()).extracting(TrainingWeek::getStartDate)
                .containsExactly(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 19));
        assertThat(phase.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 25));
    }

    @Test
    void removesWeeksBeyondTheNewDurationAndRefreshesDates() {
        TrainingPhase phase = phase(LocalDate.of(2026, 1, 5), 4);
        List<TrainingWeek> weeks = new ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            weeks.add(new TrainingWeek(phase, n));
        }
        phase.update("Fase", LocalDate.of(2026, 2, 2), 2, null);
        when(weekRepository.findByPhase(phase.getId(), USER)).thenReturn(weeks);

        structure.syncWeeks(phase);

        verify(weekRepository).delete(weeks.get(2));
        verify(weekRepository).delete(weeks.get(3));
        verify(weekRepository, never()).save(any());
        assertThat(weeks.get(1).getStartDate()).isEqualTo(LocalDate.of(2026, 2, 9));
    }

    @Test
    void weeksHaveNoDateWhenThePhaseHasNoStartDate() {
        TrainingPhase phase = phase(null, 2);
        assertThat(new TrainingWeek(phase, 2).getStartDate()).isNull();
        assertThat(phase.getEndDate()).isNull();
    }

    @Test
    void reorderRequiresTheExactSetOfItems() {
        TrainingPhase phase = phase(null, 1);
        WorkoutDay a = withId(new WorkoutDay(phase, 0));
        WorkoutDay b = withId(new WorkoutDay(phase, 1));
        List<WorkoutDay> days = List.of(a, b);

        assertThatThrownBy(() -> WorkoutDayService.reorder(days, List.of(a.getId()), WorkoutDay::getId, WorkoutDay::moveTo))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> WorkoutDayService.reorder(
                        days, List.of(a.getId(), a.getId()), WorkoutDay::getId, WorkoutDay::moveTo))
                .isInstanceOf(InvalidFieldException.class);

        WorkoutDayService.reorder(days, List.of(b.getId(), a.getId()), WorkoutDay::getId, WorkoutDay::moveTo);
        assertThat(b.getPosition()).isZero();
        assertThat(a.getPosition()).isEqualTo(1);
    }

    private static TrainingPhase phase(LocalDate start, int weeks) {
        TrainingPlan plan = withId(new TrainingPlan(USER));
        TrainingPhase phase = withId(new TrainingPhase(plan, 0));
        phase.update("Fase", start, weeks, null);
        return phase;
    }
}
