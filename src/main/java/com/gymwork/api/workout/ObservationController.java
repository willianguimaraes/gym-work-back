package com.gymwork.api.workout;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.workout.dto.ObservationRequest;
import com.gymwork.api.workout.dto.ObservationResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observations")
@Tag(name = "Observações")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping
    @Operation(summary = "Observações do período, mais recentes primeiro (datas no fuso do usuário)")
    public PageResponse<ObservationResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return observationService.period(userId, from, to, PageRequest.of(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma observação (dor, desconforto, sono, energia…)")
    public ObservationResponse create(@CurrentUserId UUID userId, @Valid @RequestBody ObservationRequest request) {
        return observationService.create(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita uma observação")
    public ObservationResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ObservationRequest request) {
        return observationService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma observação")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        observationService.delete(userId, id);
    }
}
