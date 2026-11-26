package com.gymwork.api.assessment;

import com.gymwork.api.assessment.dto.AssessmentRequest;
import com.gymwork.api.assessment.dto.AssessmentResponse;
import com.gymwork.api.assessment.dto.AssessmentSummary;
import com.gymwork.api.assessment.dto.ComparisonResponse;
import com.gymwork.api.assessment.dto.ComparisonResponse.Item;
import com.gymwork.api.assessment.dto.ComparisonResponse.PhotoPair;
import com.gymwork.api.assessment.dto.MeasurementValue;
import com.gymwork.api.assessment.dto.PhotoResponse;
import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.common.PageResponse;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import com.gymwork.api.storage.FileCleanup;
import com.gymwork.api.user.UserTime;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avaliações físicas: CRUD, comparativo entre datas e séries para gráficos. */
@Service
public class AssessmentService {

    private final BodyAssessmentRepository assessmentRepository;
    private final BodyMeasurementRepository measurementRepository;
    private final ProgressPhotoRepository photoRepository;
    private final AssessmentViews views;
    private final FileCleanup fileCleanup;
    private final UserTime userTime;

    public AssessmentService(
            BodyAssessmentRepository assessmentRepository,
            BodyMeasurementRepository measurementRepository,
            ProgressPhotoRepository photoRepository,
            AssessmentViews views,
            FileCleanup fileCleanup,
            UserTime userTime) {
        this.assessmentRepository = assessmentRepository;
        this.measurementRepository = measurementRepository;
        this.photoRepository = photoRepository;
        this.views = views;
        this.fileCleanup = fileCleanup;
        this.userTime = userTime;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssessmentSummary> list(UUID userId, Pageable pageable) {
        Page<BodyAssessment> page = assessmentRepository.findHistory(userId, pageable);
        return new PageResponse<>(views.summaries(page.getContent(), userId),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AssessmentResponse get(UUID userId, UUID assessmentId) {
        return views.detail(find(userId, assessmentId));
    }

    @Transactional
    public AssessmentResponse create(UUID userId, AssessmentRequest request) {
        BodyAssessment assessment = new BodyAssessment(userId);
        apply(userId, assessment, request);
        assessmentRepository.save(assessment);
        syncMeasurements(assessment, request.measurements());
        return views.detail(assessment);
    }

    @Transactional
    public AssessmentResponse update(UUID userId, UUID assessmentId, AssessmentRequest request) {
        BodyAssessment assessment = find(userId, assessmentId);
        apply(userId, assessment, request);
        syncMeasurements(assessment, request.measurements());
        return views.detail(assessment);
    }

    /** Exclui a avaliação com medidas e fotos (os arquivos somem depois do commit). */
    @Transactional
    public void delete(UUID userId, UUID assessmentId) {
        find(userId, assessmentId);
        List<String> keys = photoRepository.findByAssessments(List.of(assessmentId), userId).stream()
                .map(ProgressPhoto::getStorageKey)
                .toList();
        assessmentRepository.deleteByIdAndUserId(assessmentId, userId);
        fileCleanup.deleteAfterCommit(keys);
    }

    /** Comparativo entre duas avaliações (a mais antiga vira {@code from}). */
    @Transactional(readOnly = true)
    public ComparisonResponse compare(UUID userId, UUID firstId, UUID secondId) {
        if (firstId.equals(secondId)) {
            throw new InvalidFieldException("b", "Escolha duas avaliações diferentes");
        }
        BodyAssessment first = find(userId, firstId);
        BodyAssessment second = find(userId, secondId);
        boolean firstIsOlder = !first.getAssessedOn().isAfter(second.getAssessedOn());
        AssessmentResponse from = views.detail(firstIsOlder ? first : second);
        AssessmentResponse to = views.detail(firstIsOlder ? second : first);

        List<Item> items = new ArrayList<>();
        addItem(items, "WEIGHT", "kg", from.weightKg(), to.weightKg());
        addItem(items, "BODY_FAT", "%", from.bodyFatPct(), to.bodyFatPct());
        addItem(items, "HEIGHT", "cm", from.heightCm(), to.heightCm());
        Map<MeasurementType, BigDecimal> fromMeasures = measures(from.measurements());
        Map<MeasurementType, BigDecimal> toMeasures = measures(to.measurements());
        for (MeasurementType type : MeasurementType.values()) {
            addItem(items, type.name(), "cm", fromMeasures.get(type), toMeasures.get(type));
        }

        List<PhotoPair> photos = new ArrayList<>();
        for (PhotoPosition position : PhotoPosition.values()) {
            PhotoResponse before = firstPhoto(from.photos(), position);
            PhotoResponse after = firstPhoto(to.photos(), position);
            if (before != null || after != null) {
                photos.add(new PhotoPair(position, before, after));
            }
        }
        long days = ChronoUnit.DAYS.between(from.assessedOn(), to.assessedOn());
        return new ComparisonResponse(from, to, days, items, photos);
    }

    /** Valores informados em cada avaliação, da mais antiga para a mais recente. */
    @Transactional(readOnly = true)
    public List<TrendPoint> trend(UUID userId) {
        List<BodyAssessment> assessments = assessmentRepository.findAllChronological(userId);
        if (assessments.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<BodyMeasurement>> measurements = measurementRepository
                .findByAssessments(assessments.stream().map(BodyAssessment::getId).toList(), userId).stream()
                .collect(Collectors.groupingBy(m -> m.getAssessment().getId()));
        return assessments.stream()
                .map(a -> {
                    Map<MeasurementType, BigDecimal> values = new EnumMap<>(MeasurementType.class);
                    measurements.getOrDefault(a.getId(), List.of())
                            .forEach(m -> values.put(m.getType(), m.getValueCm()));
                    return new TrendPoint(a.getId(), a.getAssessedOn(), a.getWeightKg(), a.getBodyFatPct(), values);
                })
                .toList();
    }

    BodyAssessment find(UUID userId, UUID assessmentId) {
        return assessmentRepository.findByIdAndUserId(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada"));
    }

    private void apply(UUID userId, BodyAssessment assessment, AssessmentRequest request) {
        if (request.assessedOn().isAfter(userTime.today(userId))) {
            throw new InvalidFieldException("assessedOn", "A data da avaliação não pode estar no futuro");
        }
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();
        assessment.update(request.assessedOn(), request.weightKg(), request.heightCm(), request.bodyFatPct(), notes);
    }

    /** Medidas informadas substituem as atuais; as que não vierem deixam de existir. */
    private void syncMeasurements(BodyAssessment assessment, List<MeasurementValue> values) {
        List<MeasurementValue> input = values == null ? List.of() : values;
        Set<MeasurementType> seen = new HashSet<>();
        for (MeasurementValue value : input) {
            if (!seen.add(value.type())) {
                throw new InvalidFieldException("measurements", "Medida repetida: " + value.type());
            }
        }
        Map<MeasurementType, BodyMeasurement> current = measurementRepository
                .findByAssessments(List.of(assessment.getId()), assessment.getUserId()).stream()
                .collect(Collectors.toMap(BodyMeasurement::getType, Function.identity()));
        for (MeasurementValue value : input) {
            BodyMeasurement existing = current.remove(value.type());
            if (existing == null) {
                measurementRepository.save(new BodyMeasurement(assessment, value.type(), value.valueCm()));
            } else {
                existing.changeValue(value.valueCm());
            }
        }
        current.values().forEach(measurementRepository::delete);
    }

    private static void addItem(List<Item> items, String metric, String unit, BigDecimal from, BigDecimal to) {
        if (from == null && to == null) {
            return;
        }
        BigDecimal difference = from != null && to != null ? to.subtract(from) : null;
        items.add(new Item(metric, unit, from, to, difference));
    }

    private static Map<MeasurementType, BigDecimal> measures(List<MeasurementValue> values) {
        Map<MeasurementType, BigDecimal> map = new EnumMap<>(MeasurementType.class);
        values.forEach(v -> map.put(v.type(), v.valueCm()));
        return map;
    }

    private static PhotoResponse firstPhoto(List<PhotoResponse> photos, PhotoPosition position) {
        return photos.stream().filter(p -> Objects.equals(p.position(), position)).findFirst().orElse(null);
    }
}
