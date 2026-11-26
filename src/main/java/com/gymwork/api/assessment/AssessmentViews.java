package com.gymwork.api.assessment;

import com.gymwork.api.assessment.dto.AssessmentResponse;
import com.gymwork.api.assessment.dto.AssessmentSummary;
import com.gymwork.api.assessment.dto.MeasurementValue;
import com.gymwork.api.assessment.dto.PhotoResponse;
import com.gymwork.api.storage.SignedUrlService;
import com.gymwork.api.storage.SignedUrlService.SignedUrl;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Monta as respostas da avaliação física em lote, com URLs assinadas das fotos. */
@Component
class AssessmentViews {

    private final BodyMeasurementRepository measurementRepository;
    private final ProgressPhotoRepository photoRepository;
    private final SignedUrlService signedUrlService;

    AssessmentViews(
            BodyMeasurementRepository measurementRepository,
            ProgressPhotoRepository photoRepository,
            SignedUrlService signedUrlService) {
        this.measurementRepository = measurementRepository;
        this.photoRepository = photoRepository;
        this.signedUrlService = signedUrlService;
    }

    AssessmentResponse detail(BodyAssessment assessment) {
        UUID userId = assessment.getUserId();
        List<MeasurementValue> measurements = measurementRepository
                .findByAssessments(List.of(assessment.getId()), userId).stream()
                .sorted(Comparator.comparing(BodyMeasurement::getType))
                .map(m -> new MeasurementValue(m.getType(), m.getValueCm()))
                .toList();
        List<PhotoResponse> photos = photoRepository.findByAssessments(List.of(assessment.getId()), userId).stream()
                .map(this::photo)
                .toList();
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getAssessedOn(),
                assessment.getWeightKg(),
                assessment.getHeightCm(),
                assessment.getBodyFatPct(),
                assessment.getNotes(),
                measurements,
                photos);
    }

    List<AssessmentSummary> summaries(List<BodyAssessment> assessments, UUID userId) {
        if (assessments.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = assessments.stream().map(BodyAssessment::getId).toList();
        Map<UUID, Long> measurements = measurementRepository.findByAssessments(ids, userId).stream()
                .collect(Collectors.groupingBy(m -> m.getAssessment().getId(), Collectors.counting()));
        Map<UUID, Long> photos = photoRepository.findByAssessments(ids, userId).stream()
                .collect(Collectors.groupingBy(p -> p.getAssessment().getId(), Collectors.counting()));
        return assessments.stream()
                .map(a -> new AssessmentSummary(
                        a.getId(),
                        a.getAssessedOn(),
                        a.getWeightKg(),
                        a.getBodyFatPct(),
                        measurements.getOrDefault(a.getId(), 0L).intValue(),
                        photos.getOrDefault(a.getId(), 0L).intValue()))
                .toList();
    }

    PhotoResponse photo(ProgressPhoto photo) {
        SignedUrl url = signedUrlService.sign(photo.getStorageKey());
        return new PhotoResponse(
                photo.getId(),
                photo.getAssessment().getId(),
                photo.getPosition(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getWidth(),
                photo.getHeight(),
                url.url(),
                url.expiresAt(),
                photo.getCreatedAt());
    }
}
