package com.gymwork.api.exercise;

import static com.gymwork.api.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymwork.api.common.SearchText;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import com.gymwork.api.exercise.dto.ExerciseRequest;
import com.gymwork.api.exercise.dto.ExerciseResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private MuscleGroupRepository muscleGroupRepository;

    @InjectMocks
    private ExerciseService service;

    @Test
    void createsCustomExerciseWithNormalizedNameAndWithoutRepeatingThePrimaryMuscle() {
        when(muscleGroupRepository.existsById(anyString())).thenReturn(true);
        when(exerciseRepository.save(any())).thenAnswer(call -> withId(call.getArgument(0)));

        ExerciseResponse response = service.create(USER, request("  Remada   baixa  unilateral ", List.of("BICEPS", "BACK")));

        assertThat(response.name()).isEqualTo("Remada baixa unilateral");
        assertThat(response.secondaryMuscleGroups()).containsExactly("BICEPS");
        assertThat(response.custom()).isTrue();
    }

    @Test
    void rejectsNameAlreadyUsedByAnotherVisibleExercise() {
        Exercise existing = withId(Exercise.custom(null));
        when(exerciseRepository.findSameName(USER, "Puxada neutra", Equipment.CABLE)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(USER, request("Puxada neutra", List.of())))
                .isInstanceOf(InvalidFieldException.class)
                .extracting("field").isEqualTo("name");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void editingKeepingTheSameNameIsNotADuplicate() {
        Exercise mine = withId(Exercise.custom(USER));
        mine.update("Puxada neutra", "BACK", Set.of(), ExerciseType.COMPOUND, Equipment.CABLE, null, null);
        when(exerciseRepository.findByIdAndOwnerId(mine.getId(), USER)).thenReturn(Optional.of(mine));
        when(exerciseRepository.findSameName(USER, "Puxada neutra", Equipment.CABLE)).thenReturn(List.of(mine));
        when(muscleGroupRepository.existsById(anyString())).thenReturn(true);

        ExerciseResponse response = service.update(USER, mine.getId(), request("Puxada neutra", List.of()));

        assertThat(response.description()).isEqualTo("Pegada neutra");
    }

    @Test
    void onlyOwnedExercisesCanBeEdited() {
        UUID globalId = UUID.randomUUID();
        when(exerciseRepository.findByIdAndOwnerId(globalId, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(USER, globalId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveExercisesCannotBeAddedToPlans() {
        Exercise inactive = withId(Exercise.custom(USER));
        inactive.setActive(false);
        when(exerciseRepository.findVisible(inactive.getId(), USER)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.requireUsable(USER, inactive.getId(), "exerciseId"))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage("Exercício desativado");
    }

    @Test
    void searchTextIgnoresAccentsCaseAndExtraSpaces() {
        assertThat(SearchText.normalize("  Elevação   LATERAL ")).isEqualTo("elevacao lateral");
        assertThat(SearchText.normalize("Glúteo na polia (coice)")).isEqualTo("gluteo na polia (coice)");
        assertThat(SearchText.normalize(null)).isEmpty();
    }

    private static ExerciseRequest request(String name, List<String> secondaries) {
        return new ExerciseRequest(
                name, "BACK", secondaries, ExerciseType.COMPOUND, Equipment.CABLE, " Pegada neutra ", null);
    }
}
