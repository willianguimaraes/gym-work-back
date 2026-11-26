package com.gymwork.api.exercise;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import com.gymwork.api.exercise.dto.ExerciseRequest;
import com.gymwork.api.exercise.dto.ExerciseResponse;
import com.gymwork.api.exercise.dto.MuscleGroupResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de exercícios. O usuário vê os globais e os dele; só edita os dele. Todo método recebe
 * o ID do usuário autenticado.
 */
@Service
public class ExerciseService {

    static final String NOT_FOUND = "Exercício não encontrado";

    private final ExerciseRepository exerciseRepository;
    private final MuscleGroupRepository muscleGroupRepository;

    public ExerciseService(ExerciseRepository exerciseRepository, MuscleGroupRepository muscleGroupRepository) {
        this.exerciseRepository = exerciseRepository;
        this.muscleGroupRepository = muscleGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<MuscleGroupResponse> muscleGroups() {
        return muscleGroupRepository.findAllByOrderByPositionAsc().stream()
                .map(MuscleGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ExerciseResponse> search(UUID userId, ExerciseSearch search, Pageable pageable) {
        List<Specification<Exercise>> filters = Stream.of(
                        ExerciseSpecifications.visibleTo(userId, search.scope()),
                        ExerciseSpecifications.nameContains(search.text()),
                        ExerciseSpecifications.muscleGroup(search.muscleGroup()),
                        ExerciseSpecifications.equipment(search.equipment()),
                        ExerciseSpecifications.activeOnly(search.includeInactive()))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(
                exerciseRepository.findAll(Specification.allOf(filters), pageable), ExerciseResponse::from);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse get(UUID userId, UUID exerciseId) {
        return ExerciseResponse.from(visible(userId, exerciseId));
    }

    @Transactional
    public ExerciseResponse create(UUID userId, ExerciseRequest request) {
        Exercise exercise = Exercise.custom(userId);
        apply(userId, exercise, request);
        return ExerciseResponse.from(exerciseRepository.save(exercise));
    }

    @Transactional
    public ExerciseResponse update(UUID userId, UUID exerciseId, ExerciseRequest request) {
        Exercise exercise = owned(userId, exerciseId);
        apply(userId, exercise, request);
        return ExerciseResponse.from(exercise);
    }

    @Transactional
    public ExerciseResponse setActive(UUID userId, UUID exerciseId, boolean active) {
        Exercise exercise = owned(userId, exerciseId);
        exercise.setActive(active);
        return ExerciseResponse.from(exercise);
    }

    /** Catálogo utilizável pelo usuário (globais e personalizados ativos). */
    @Transactional(readOnly = true)
    public List<Exercise> usableCatalog(UUID userId) {
        return exerciseRepository.findUsable(userId);
    }

    /**
     * Exercício que o usuário pode colocar num plano: global ou dele, e ativo. Usado pelo
     * planejamento para nunca aceitar o exercício personalizado de outro usuário.
     */
    @Transactional(readOnly = true)
    public Exercise requireUsable(UUID userId, UUID exerciseId, String field) {
        Exercise exercise = exerciseRepository.findVisible(exerciseId, userId)
                .orElseThrow(() -> new InvalidFieldException(field, NOT_FOUND));
        if (!exercise.isActive()) {
            throw new InvalidFieldException(field, "Exercício desativado");
        }
        return exercise;
    }

    /** Exercício global ou do usuário (ativo ou não), referenciado num campo da requisição. */
    @Transactional(readOnly = true)
    public Exercise requireVisible(UUID userId, UUID exerciseId, String field) {
        return exerciseRepository.findVisible(exerciseId, userId)
                .orElseThrow(() -> new InvalidFieldException(field, NOT_FOUND));
    }

    private void apply(UUID userId, Exercise exercise, ExerciseRequest request) {
        String name = request.name().trim().replaceAll("\\s+", " ");
        boolean duplicated = exerciseRepository.findSameName(userId, name, request.equipment()).stream()
                .anyMatch(other -> !other.equals(exercise));
        if (duplicated) {
            throw new InvalidFieldException("name", "Já existe um exercício com esse nome e equipamento");
        }
        String primary = request.primaryMuscleGroup();
        requireMuscleGroup(primary, "primaryMuscleGroup");
        Set<String> secondaries = new LinkedHashSet<>();
        for (String code : Objects.requireNonNullElse(request.secondaryMuscleGroups(), List.<String>of())) {
            requireMuscleGroup(code, "secondaryMuscleGroups");
            if (!code.equals(primary)) {
                secondaries.add(code);
            }
        }
        exercise.update(
                name, primary, secondaries, request.type(), request.equipment(),
                blankToNull(request.description()), blankToNull(request.instructions()));
    }

    private void requireMuscleGroup(String code, String field) {
        if (code == null || !muscleGroupRepository.existsById(code)) {
            throw new InvalidFieldException(field, "Grupo muscular inválido");
        }
    }

    private Exercise visible(UUID userId, UUID exerciseId) {
        return exerciseRepository.findVisible(exerciseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Só exercícios personalizados do usuário; globais e de outros usuários respondem 404. */
    private Exercise owned(UUID userId, UUID exerciseId) {
        return exerciseRepository.findByIdAndOwnerId(exerciseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercício personalizado não encontrado"));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
