package com.gymwork.api.training;

import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.training.dto.PlanRequest;
import com.gymwork.api.training.dto.PlanResponse;
import com.gymwork.api.training.dto.PlanSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/plans")
@Tag(name = "Planejamentos")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "Planejamentos do usuário (mais recentes primeiro)")
    public List<PlanSummaryResponse> list(@CurrentUserId UUID userId) {
        return planService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um planejamento (rascunho, se o status não for informado)")
    public PlanResponse create(@CurrentUserId UUID userId, @Valid @RequestBody PlanRequest request) {
        return planService.create(userId, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe do planejamento, com as fases")
    public PlanResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return planService.get(userId, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita o planejamento")
    public PlanResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return planService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui o planejamento com fases, semanas, dias e exercícios")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        planService.delete(userId, id);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Torna o planejamento ativo (o ativo anterior passa a concluído)")
    public PlanResponse activate(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return planService.activate(userId, id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Duplica o planejamento como rascunho")
    public PlanResponse duplicate(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return planService.duplicate(userId, id);
    }
}
