package com.gymwork.api.workout;

import com.gymwork.api.common.PageResponse;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.dto.ObservationRequest;
import com.gymwork.api.workout.dto.ObservationResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observações de treino (dor, desconforto, sono, energia…). Guarda só o relato do usuário: nenhum
 * diagnóstico ou recomendação é gerado a partir delas.
 */
@Service
public class ObservationService {

    private final WorkoutObservationRepository observationRepository;
    private final ExerciseService exerciseService;
    private final WorkoutLookup lookup;
    private final UserTime userTime;
    private final Clock clock;

    public ObservationService(
            WorkoutObservationRepository observationRepository,
            ExerciseService exerciseService,
            WorkoutLookup lookup,
            UserTime userTime,
            Clock clock) {
        this.observationRepository = observationRepository;
        this.exerciseService = exerciseService;
        this.lookup = lookup;
        this.userTime = userTime;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ObservationResponse> bySession(UUID userId, UUID sessionId) {
        lookup.session(userId, sessionId);
        return observationRepository.findByUserIdAndSessionIdOrderByObservedAtAsc(userId, sessionId).stream()
                .map(ObservationResponse::from)
                .toList();
    }

    /** Observações do período (datas no fuso do usuário, fim inclusivo), mais recentes primeiro. */
    @Transactional(readOnly = true)
    public PageResponse<ObservationResponse> period(UUID userId, LocalDate from, LocalDate to, Pageable pageable) {
        ZoneId zone = userTime.zone(userId);
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay(zone).toInstant();
        return PageResponse.of(observationRepository.findPeriod(userId, start, end, pageable), ObservationResponse::from);
    }

    @Transactional
    public ObservationResponse create(UUID userId, ObservationRequest request) {
        WorkoutObservation observation = new WorkoutObservation(userId);
        observation.update(validate(userId, request, null));
        return ObservationResponse.from(observationRepository.save(observation));
    }

    @Transactional
    public ObservationResponse update(UUID userId, UUID observationId, ObservationRequest request) {
        WorkoutObservation observation = lookup.observation(userId, observationId);
        observation.update(validate(userId, request, observation.getObservedAt()));
        return ObservationResponse.from(observation);
    }

    @Transactional
    public void delete(UUID userId, UUID observationId) {
        observationRepository.delete(lookup.observation(userId, observationId));
    }

    private ObservationData validate(UUID userId, ObservationRequest request, Instant previousObservedAt) {
        ObservationCategory category = request.category();
        UUID sessionId = request.sessionId();
        UUID sessionExerciseId = request.sessionExerciseId();
        UUID exerciseId = request.exerciseId();

        if (sessionExerciseId != null) {
            SessionExercise sessionExercise = lookup.sessionExerciseField(userId, sessionExerciseId, "sessionExerciseId");
            UUID ownerSession = sessionExercise.getSession().getId();
            if (sessionId != null && !sessionId.equals(ownerSession)) {
                throw new InvalidFieldException("sessionExerciseId", "O exercício não é desta sessão");
            }
            sessionId = ownerSession;
            exerciseId = sessionExercise.getExercise().getId();
        } else if (sessionId != null) {
            lookup.sessionField(userId, sessionId, "sessionId");
        }
        if (request.sessionExerciseId() == null && exerciseId != null) {
            exerciseId = exerciseService.requireVisible(userId, exerciseId, "exerciseId").getId();
        }

        if (category.isBodyRelated()) {
            if (request.bodyRegion() == null) {
                throw new InvalidFieldException("bodyRegion", "Informe a região do corpo");
            }
        } else if (request.bodyRegion() != null || request.bodySide() != null) {
            throw new InvalidFieldException("bodyRegion", "Região e lado do corpo só valem para dor ou desconforto");
        }
        String text = request.text() == null || request.text().isBlank() ? null : request.text().trim();
        if (category == ObservationCategory.GENERAL && text == null) {
            throw new InvalidFieldException("text", "Escreva a observação");
        }
        Instant observedAt = request.observedAt() != null
                ? request.observedAt()
                : (previousObservedAt != null ? previousObservedAt : clock.instant());
        return new ObservationData(
                sessionId,
                sessionExerciseId,
                exerciseId,
                category,
                request.bodyRegion(),
                request.bodySide(),
                request.intensity(),
                request.moment(),
                Boolean.TRUE.equals(request.interrupted()),
                text,
                observedAt);
    }
}
