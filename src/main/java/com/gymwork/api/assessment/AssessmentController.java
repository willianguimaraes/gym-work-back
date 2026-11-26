package com.gymwork.api.assessment;

import com.gymwork.api.assessment.dto.AssessmentRequest;
import com.gymwork.api.assessment.dto.AssessmentResponse;
import com.gymwork.api.assessment.dto.AssessmentSummary;
import com.gymwork.api.assessment.dto.ComparisonResponse;
import com.gymwork.api.assessment.dto.PhotoResponse;
import com.gymwork.api.assessment.dto.PhotoUrlResponse;
import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.common.PageResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Avaliação física")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final PhotoService photoService;

    public AssessmentController(AssessmentService assessmentService, PhotoService photoService) {
        this.assessmentService = assessmentService;
        this.photoService = photoService;
    }

    public record PositionRequest(@NotNull PhotoPosition position) {
    }

    @GetMapping("/assessments")
    @Operation(summary = "Avaliações, mais recentes primeiro")
    public PageResponse<AssessmentSummary> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return assessmentService.list(userId, PageRequest.of(page, size));
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma avaliação (tudo opcional, menos a data; só o peso = registro rápido)")
    public AssessmentResponse create(@CurrentUserId UUID userId, @Valid @RequestBody AssessmentRequest request) {
        return assessmentService.create(userId, request);
    }

    @GetMapping("/assessments/trend")
    @Operation(summary = "Peso, gordura e medidas de todas as avaliações (para gráficos)")
    public List<TrendPoint> trend(@CurrentUserId UUID userId) {
        return assessmentService.trend(userId);
    }

    @GetMapping("/assessments/compare")
    @Operation(summary = "Comparativo entre duas avaliações (medidas e fotos por posição)")
    public ComparisonResponse compare(@CurrentUserId UUID userId, @RequestParam UUID a, @RequestParam UUID b) {
        return assessmentService.compare(userId, a, b);
    }

    @GetMapping("/assessments/{id}")
    @Operation(summary = "Avaliação com medidas e fotos (URLs temporárias)")
    public AssessmentResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return assessmentService.get(userId, id);
    }

    @PutMapping("/assessments/{id}")
    @Operation(summary = "Edita a avaliação; as medidas enviadas substituem as atuais")
    public AssessmentResponse update(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody AssessmentRequest request) {
        return assessmentService.update(userId, id, request);
    }

    @DeleteMapping("/assessments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a avaliação com medidas e fotos")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        assessmentService.delete(userId, id);
    }

    @PostMapping(path = "/assessments/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia uma foto (JPEG, PNG ou WebP, até 10 MB)")
    public PhotoResponse upload(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "OTHER") PhotoPosition position) {
        return photoService.upload(userId, id, file, position);
    }

    @GetMapping("/photos/{id}/url")
    @Operation(summary = "Nova URL temporária da foto (5 minutos)")
    public PhotoUrlResponse photoUrl(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return photoService.url(userId, id);
    }

    @PatchMapping("/photos/{id}")
    @Operation(summary = "Muda a posição da foto (frente, lados, costas, outra)")
    public PhotoResponse movePhoto(
            @CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody PositionRequest request) {
        return photoService.move(userId, id, request.position());
    }

    @DeleteMapping("/photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a foto")
    public void deletePhoto(@CurrentUserId UUID userId, @PathVariable UUID id) {
        photoService.delete(userId, id);
    }
}
