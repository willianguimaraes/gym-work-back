package com.gymwork.api.training;

import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.training.dto.PhaseRequest;
import com.gymwork.api.training.dto.PhaseResponse;
import com.gymwork.api.training.dto.WeekRequest;
import com.gymwork.api.training.dto.WeekResponse;
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
@Tag(name = "Fases e semanas")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class PhaseController {

    private final PhaseService phaseService;

    public PhaseController(PhaseService phaseService) {
        this.phaseService = phaseService;
    }

    @PostMapping("/plans/{planId}/phases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma fase no fim do planejamento (gera as semanas)")
    public PhaseResponse create(
            @CurrentUserId UUID userId, @PathVariable UUID planId, @Valid @RequestBody PhaseRequest request) {
        return phaseService.create(userId, planId, request);
    }

    @GetMapping("/phases/{id}")
    @Operation(summary = "Detalhe da fase: semanas, dias e exercícios")
    public PhaseResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return phaseService.get(userId, id);
    }

    @PutMapping("/phases/{id}")
    @Operation(summary = "Edita a fase (mudar a duração cria/remove semanas no fim)")
    public PhaseResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody PhaseRequest request) {
        return phaseService.update(userId, id, request);
    }

    @DeleteMapping("/phases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a fase com semanas, dias e exercícios")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        phaseService.delete(userId, id);
    }

    @PostMapping("/phases/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma nova fase a partir desta, no fim do mesmo planejamento")
    public PhaseResponse duplicate(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return phaseService.duplicate(userId, id);
    }

    @PutMapping("/weeks/{id}")
    @Operation(summary = "Marca deload e observações da semana")
    public WeekResponse updateWeek(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody WeekRequest request) {
        return phaseService.updateWeek(userId, id, request);
    }
}
