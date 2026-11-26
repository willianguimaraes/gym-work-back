package com.gymwork.api.progression;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.progression.ExerciseEvolutionService.GroupBy;
import com.gymwork.api.progression.dto.ExerciseHistoryItem;
import com.gymwork.api.progression.dto.ExerciseSummaryResponse;
import com.gymwork.api.progression.dto.ProgressPoint;
import com.gymwork.api.progression.dto.ProgressionReport;
import com.gymwork.api.progression.dto.RuleRequest;
import com.gymwork.api.progression.dto.RuleResponse;
import com.gymwork.api.progression.dto.VolumeReport;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Evolução e progressão")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ProgressionController {

    private final ExerciseEvolutionService evolutionService;
    private final ProgressionService progressionService;
    private final ProgressionRuleService ruleService;

    public ProgressionController(
            ExerciseEvolutionService evolutionService,
            ProgressionService progressionService,
            ProgressionRuleService ruleService) {
        this.evolutionService = evolutionService;
        this.progressionService = progressionService;
        this.ruleService = ruleService;
    }

    @GetMapping("/exercises/{id}/history")
    @Operation(summary = "Execuções do exercício (sessões finalizadas), mais recentes primeiro")
    public PageResponse<ExerciseHistoryItem> history(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return evolutionService.history(userId, id, PageRequest.of(page, size));
    }

    @GetMapping("/exercises/{id}/summary")
    @Operation(summary = "Última execução, melhor desempenho, análise de progressão, regra e observações")
    public ExerciseSummaryResponse summary(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return evolutionService.summary(userId, id);
    }

    @GetMapping("/exercises/{id}/progress")
    @Operation(summary = "Dados do gráfico de carga e repetições, por sessão, semana ou fase")
    public List<ProgressPoint> progress(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "SESSION") GroupBy groupBy) {
        return evolutionService.progress(userId, id, groupBy);
    }

    @GetMapping("/exercises/{id}/progression-rule")
    @Operation(summary = "Regra de progressão do exercício (padrão, se não houver ajuste)")
    public RuleResponse getRule(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return ruleService.get(userId, id);
    }

    @PutMapping("/exercises/{id}/progression-rule")
    @Operation(summary = "Ajusta a regra: RIR mínimo, sessões no teto, incremento, ativa/desativa")
    public RuleResponse updateRule(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return ruleService.update(userId, id, request);
    }

    @DeleteMapping("/exercises/{id}/progression-rule")
    @Operation(summary = "Volta à regra padrão")
    public RuleResponse resetRule(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return ruleService.reset(userId, id);
    }

    @GetMapping("/progression")
    @Operation(summary = "Exercícios em progresso, estáveis e em queda, com sugestão (da fase ou das últimas semanas)")
    public ProgressionReport report(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) UUID phaseId,
            @RequestParam(defaultValue = "8") @Min(1) @Max(52) int weeks) {
        return progressionService.report(userId, phaseId, weeks);
    }

    @GetMapping("/progression/volume")
    @Operation(summary = "Volume semanal: sessões, séries válidas e séries por grupo muscular")
    public VolumeReport volume(
            @CurrentUserId UUID userId, @RequestParam(defaultValue = "8") @Min(1) @Max(52) int weeks) {
        return progressionService.volume(userId, weeks);
    }
}
