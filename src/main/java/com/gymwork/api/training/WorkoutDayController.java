package com.gymwork.api.training;

import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.training.dto.DayRequest;
import com.gymwork.api.training.dto.DayResponse;
import com.gymwork.api.training.dto.PhaseResponse;
import com.gymwork.api.training.dto.PlannedExerciseRequest;
import com.gymwork.api.training.dto.PlannedExerciseResponse;
import com.gymwork.api.training.dto.ReorderRequest;
import com.gymwork.api.training.dto.ReplaceExerciseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Dias de treino")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class WorkoutDayController {

    private final WorkoutDayService dayService;

    public WorkoutDayController(WorkoutDayService dayService) {
        this.dayService = dayService;
    }

    @PostMapping("/phases/{phaseId}/days")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um dia de treino no fim da fase")
    public DayResponse create(
            @CurrentUserId UUID userId, @PathVariable UUID phaseId, @Valid @RequestBody DayRequest request) {
        return dayService.create(userId, phaseId, request);
    }

    @PutMapping("/phases/{phaseId}/days/order")
    @Operation(summary = "Reordena os dias da fase (lista com todos os IDs, na nova ordem)")
    public PhaseResponse reorderDays(
            @CurrentUserId UUID userId, @PathVariable UUID phaseId, @Valid @RequestBody ReorderRequest request) {
        return dayService.reorderDays(userId, phaseId, request.ids());
    }

    @GetMapping("/days/{id}")
    @Operation(summary = "Dia de treino com os exercícios em ordem")
    public DayResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return dayService.get(userId, id);
    }

    @PutMapping("/days/{id}")
    @Operation(summary = "Edita o dia de treino")
    public DayResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody DayRequest request) {
        return dayService.update(userId, id, request);
    }

    @DeleteMapping("/days/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui o dia de treino e seus exercícios")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        dayService.delete(userId, id);
    }

    @PostMapping("/days/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona um exercício no fim do dia")
    public PlannedExerciseResponse addExercise(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody PlannedExerciseRequest request) {
        return dayService.addExercise(userId, id, request);
    }

    @PutMapping("/days/{id}/exercises/order")
    @Operation(summary = "Reordena os exercícios do dia (lista com todos os IDs, na nova ordem)")
    public DayResponse reorderExercises(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
        return dayService.reorderExercises(userId, id, request.ids());
    }

    @PutMapping("/planned-exercises/{id}")
    @Operation(summary = "Edita séries, repetições, descanso, carga sugerida, RIR e observações")
    public PlannedExerciseResponse updateExercise(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody PlannedExerciseRequest request) {
        return dayService.updateExercise(userId, id, request);
    }

    @DeleteMapping("/planned-exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove o exercício do dia")
    public void removeExercise(@CurrentUserId UUID userId, @PathVariable UUID id) {
        dayService.removeExercise(userId, id);
    }

    @PostMapping("/planned-exercises/{id}/replace")
    @Operation(summary = "Substitui o exercício mantendo a configuração")
    public PlannedExerciseResponse replaceExercise(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ReplaceExerciseRequest request) {
        return dayService.replaceExercise(userId, id, request.exerciseId());
    }
}
