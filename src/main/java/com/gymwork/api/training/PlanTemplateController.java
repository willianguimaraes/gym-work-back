package com.gymwork.api.training;

import com.gymwork.api.common.Slug;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.training.dto.PlanImportPreview;
import com.gymwork.api.training.dto.PlanImportRequest;
import com.gymwork.api.training.dto.PlanImportResult;
import com.gymwork.api.training.dto.PlanTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Planejamento")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class PlanTemplateController {

    private final PlanTemplateService templateService;
    private final PlanImportService importService;

    public PlanTemplateController(PlanTemplateService templateService, PlanImportService importService) {
        this.templateService = templateService;
        this.importService = importService;
    }

    @GetMapping("/plans/{id}/template")
    @Operation(summary = "Planejamento no template gymwork.plan/v1 (JSON); download=true baixa como arquivo")
    public ResponseEntity<PlanTemplate> plan(
            @CurrentUserId UUID userId, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        PlanTemplate template = templateService.plan(userId, id);
        return respond(template, template.plan().name(), download);
    }

    @GetMapping("/phases/{id}/template")
    @Operation(summary = "Uma fase (com os dados do plano) no template gymwork.plan/v1")
    public ResponseEntity<PlanTemplate> phase(
            @CurrentUserId UUID userId, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        PlanTemplate template = templateService.phase(userId, id);
        return respond(template, template.phases().get(0).name(), download);
    }

    @PostMapping("/plans/import/preview")
    @Operation(summary = "Valida um JSON gymwork.plan/v1 e mostra o que será criado, sem gravar")
    public PlanImportPreview preview(@CurrentUserId UUID userId, @Valid @RequestBody PlanImportRequest request) {
        return importService.preview(userId, request);
    }

    @PostMapping("/plans/import")
    @Operation(summary = "Importa o JSON gymwork.plan/v1: cria um planejamento em rascunho ou, com planId, adiciona as fases ao fim dele")
    public ResponseEntity<PlanImportResult> importPlan(
            @CurrentUserId UUID userId, @Valid @RequestBody PlanImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importService.importPlan(userId, request));
    }

    private static ResponseEntity<PlanTemplate> respond(PlanTemplate template, String name, boolean download) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (download) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename("plano-" + Slug.of(name) + ".json")
                    .build().toString());
        }
        return response.body(template);
    }
}
