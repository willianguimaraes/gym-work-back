package com.gymwork.api.exercise;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.exercise.dto.ActiveRequest;
import com.gymwork.api.exercise.dto.ExerciseRequest;
import com.gymwork.api.exercise.dto.ExerciseResponse;
import com.gymwork.api.exercise.dto.MuscleGroupResponse;
import com.gymwork.api.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Catálogo de exercícios")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ExerciseController {

    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("equipment"));

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/muscle-groups")
    @Operation(summary = "Grupos musculares")
    public List<MuscleGroupResponse> muscleGroups() {
        return exerciseService.muscleGroups();
    }

    @GetMapping("/exercises")
    @Operation(summary = "Busca no catálogo (globais + personalizados), ordenada por nome")
    public PageResponse<ExerciseResponse> search(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(required = false) Equipment equipment,
            @RequestParam(defaultValue = "ALL") ExerciseScope scope,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return exerciseService.search(
                userId,
                new ExerciseSearch(search, muscleGroup, equipment, scope, includeInactive),
                PageRequest.of(page, size, BY_NAME));
    }

    @GetMapping("/exercises/{id}")
    @Operation(summary = "Detalhe de um exercício")
    public ExerciseResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return exerciseService.get(userId, id);
    }

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um exercício personalizado")
    public ExerciseResponse create(@CurrentUserId UUID userId, @Valid @RequestBody ExerciseRequest request) {
        return exerciseService.create(userId, request);
    }

    @PutMapping("/exercises/{id}")
    @Operation(summary = "Edita um exercício personalizado (os globais não podem ser editados)")
    public ExerciseResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ExerciseRequest request) {
        return exerciseService.update(userId, id, request);
    }

    @PatchMapping("/exercises/{id}/active")
    @Operation(summary = "Ativa ou desativa um exercício personalizado")
    public ExerciseResponse setActive(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return exerciseService.setActive(userId, id, request.active());
    }
}
