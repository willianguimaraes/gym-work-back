package com.gymwork.api.workout;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.workout.dto.ExerciseIdRequest;
import com.gymwork.api.workout.dto.NotesRequest;
import com.gymwork.api.workout.dto.SessionExerciseResponse;
import com.gymwork.api.workout.dto.SessionResponse;
import com.gymwork.api.workout.dto.SessionSummaryResponse;
import com.gymwork.api.workout.dto.SetRequest;
import com.gymwork.api.workout.dto.StartOptionsResponse;
import com.gymwork.api.workout.dto.StartSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Execução do treino")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class SessionController {

    private final SessionService sessionService;
    private final SessionExerciseService exerciseService;

    public SessionController(SessionService sessionService, SessionExerciseService exerciseService) {
        this.sessionService = sessionService;
        this.exerciseService = exerciseService;
    }

    // ------------------------------------------------------------------------------- sessões

    @GetMapping("/sessions/start-options")
    @Operation(summary = "Tela Treinar: sessão em andamento ou dias da fase atual do planejamento ativo")
    public StartOptionsResponse startOptions(
            @CurrentUserId UUID userId, @RequestParam(required = false) UUID phaseId) {
        return sessionService.startOptions(userId, phaseId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inicia uma sessão a partir de um dia do plano, ou avulsa (409 se já houver uma em andamento)")
    public SessionResponse start(@CurrentUserId UUID userId, @Valid @RequestBody StartSessionRequest request) {
        return sessionService.start(userId, request);
    }

    @GetMapping("/sessions/current")
    @Operation(summary = "Sessão em andamento (204 se não houver)")
    public ResponseEntity<SessionResponse> current(@CurrentUserId UUID userId) {
        return sessionService.current(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions")
    @Operation(summary = "Histórico de sessões, mais recentes primeiro (datas no fuso do usuário)")
    public PageResponse<SessionSummaryResponse> history(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sessionService.history(userId, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Sessão com exercícios, séries, última execução de cada exercício e observações")
    public SessionResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return sessionService.get(userId, id);
    }

    @PostMapping("/sessions/{id}/finish")
    @Operation(summary = "Finaliza a sessão, mesmo com exercícios ou séries não feitos")
    public SessionResponse finish(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody(required = false) NotesRequest request) {
        return sessionService.finish(userId, id, request == null ? null : request.notes());
    }

    @PatchMapping("/sessions/{id}")
    @Operation(summary = "Altera as observações gerais da sessão")
    public SessionResponse updateNotes(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody NotesRequest request) {
        return sessionService.updateNotes(userId, id, request.notes());
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Descarta a sessão com as séries e observações dela")
    public void discard(@CurrentUserId UUID userId, @PathVariable UUID id) {
        sessionService.discard(userId, id);
    }

    @PostMapping("/sessions/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona um exercício extra no fim da sessão")
    public SessionExerciseResponse addExercise(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ExerciseIdRequest request) {
        return sessionService.addExercise(userId, id, request.exerciseId());
    }

    // ------------------------------------------------------------------------------- exercícios

    @PostMapping("/session-exercises/{id}/skip")
    @Operation(summary = "Pula o exercício")
    public SessionExerciseResponse skip(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return exerciseService.skip(userId, id);
    }

    @PostMapping("/session-exercises/{id}/unskip")
    @Operation(summary = "Desfaz o pular")
    public SessionExerciseResponse unskip(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return exerciseService.unskip(userId, id);
    }

    @PostMapping("/session-exercises/{id}/substitute")
    @Operation(summary = "Substitui o exercício (o novo entra logo depois, com a mesma configuração)")
    public SessionResponse substitute(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ExerciseIdRequest request) {
        return exerciseService.substitute(userId, id, request.exerciseId());
    }

    @PatchMapping("/session-exercises/{id}")
    @Operation(summary = "Altera as observações do exercício na sessão")
    public SessionExerciseResponse updateExerciseNotes(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody NotesRequest request) {
        return exerciseService.updateNotes(userId, id, request.notes());
    }

    // ------------------------------------------------------------------------------- séries

    @PostMapping("/session-exercises/{id}/sets")
    @Operation(summary = "Registra uma série; reenviar o mesmo clientId não duplica (fila offline)")
    public SessionExerciseResponse addSet(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody SetRequest request) {
        return exerciseService.addSet(userId, id, request);
    }

    @PutMapping("/sets/{id}")
    @Operation(summary = "Corrige uma série")
    public SessionExerciseResponse updateSet(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody SetRequest request) {
        return exerciseService.updateSet(userId, id, request);
    }

    @DeleteMapping("/sets/{id}")
    @Operation(summary = "Remove uma série (devolve o exercício atualizado)")
    public SessionExerciseResponse deleteSet(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return exerciseService.deleteSet(userId, id);
    }
}
