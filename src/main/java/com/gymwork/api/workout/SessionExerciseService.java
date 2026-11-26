package com.gymwork.api.workout;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.exercise.Exercise;
import com.gymwork.api.exercise.ExerciseService;
import com.gymwork.api.workout.dto.SessionExerciseResponse;
import com.gymwork.api.workout.dto.SessionResponse;
import com.gymwork.api.workout.dto.SetRequest;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ações sobre um exercício da sessão: registrar, corrigir e remover séries; pular; substituir.
 * Cada série é salva na hora. Os métodos de série devolvem o exercício atualizado (séries e status).
 */
@Service
public class SessionExerciseService {

    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseService exerciseService;
    private final WorkoutLookup lookup;
    private final SessionViews views;
    private final Clock clock;

    public SessionExerciseService(
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseSetRepository setRepository,
            ExerciseService exerciseService,
            WorkoutLookup lookup,
            SessionViews views,
            Clock clock) {
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.setRepository = setRepository;
        this.exerciseService = exerciseService;
        this.lookup = lookup;
        this.views = views;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------- séries

    /**
     * Registra uma série. Com {@code clientId} já usado pelo usuário, devolve o registro existente
     * sem duplicar (reenvio da fila offline).
     */
    @Transactional
    public SessionExerciseResponse addSet(UUID userId, UUID sessionExerciseId, SetRequest request) {
        if (request.clientId() != null) {
            Optional<ExerciseSet> existing = setRepository.findByUserIdAndClientId(userId, request.clientId());
            if (existing.isPresent()) {
                return views.exercise(existing.get().getSessionExercise());
            }
        }
        SessionExercise exercise = lookup.sessionExercise(userId, sessionExerciseId);
        if (exercise.getStatus() == SessionExerciseStatus.SUBSTITUTED) {
            throw new InvalidFieldException("sessionExerciseId", "Este exercício foi substituído");
        }
        ExerciseSet set = new ExerciseSet(
                exercise, request.clientId(), setRepository.maxSetNumber(exercise.getId()) + 1, clock.instant());
        set.record(validate(exercise, request));
        setRepository.save(set);
        exercise.unskip();
        refreshStatus(exercise);
        return views.exercise(exercise);
    }

    @Transactional
    public SessionExerciseResponse updateSet(UUID userId, UUID setId, SetRequest request) {
        ExerciseSet set = lookup.set(userId, setId);
        SessionExercise exercise = set.getSessionExercise();
        if (request.parentSetId() != null && request.parentSetId().equals(set.getId())) {
            throw new InvalidFieldException("parentSetId", "A série não pode ser a principal de si mesma");
        }
        set.record(validate(exercise, request));
        refreshStatus(exercise);
        return views.exercise(exercise);
    }

    @Transactional
    public SessionExerciseResponse deleteSet(UUID userId, UUID setId) {
        ExerciseSet set = lookup.set(userId, setId);
        SessionExercise exercise = set.getSessionExercise();
        setRepository.delete(set);
        refreshStatus(exercise);
        return views.exercise(exercise);
    }

    // ------------------------------------------------------------------------------- exercício

    @Transactional
    public SessionExerciseResponse skip(UUID userId, UUID sessionExerciseId) {
        SessionExercise exercise = lookup.sessionExercise(userId, sessionExerciseId);
        if (exercise.getStatus() == SessionExerciseStatus.SUBSTITUTED) {
            throw new InvalidFieldException("sessionExerciseId", "Este exercício foi substituído");
        }
        exercise.skip();
        return views.exercise(exercise);
    }

    /** Desfaz o "pular": o status volta a ser calculado pelas séries. */
    @Transactional
    public SessionExerciseResponse unskip(UUID userId, UUID sessionExerciseId) {
        SessionExercise exercise = lookup.sessionExercise(userId, sessionExerciseId);
        exercise.unskip();
        refreshStatus(exercise);
        return views.exercise(exercise);
    }

    /**
     * Substitui o exercício (ex.: aparelho ocupado): o original fica como SUBSTITUTED, com as séries
     * que já tiver, e o novo entra logo depois dele com a mesma configuração planejada.
     */
    @Transactional
    public SessionResponse substitute(UUID userId, UUID sessionExerciseId, UUID exerciseId) {
        SessionExercise original = lookup.sessionExercise(userId, sessionExerciseId);
        if (original.getStatus() == SessionExerciseStatus.SUBSTITUTED) {
            throw new InvalidFieldException("sessionExerciseId", "Este exercício já foi substituído");
        }
        Exercise exercise = exerciseService.requireUsable(userId, exerciseId, "exerciseId");
        if (exercise.getId().equals(original.getExercise().getId())) {
            throw new InvalidFieldException("exerciseId", "Escolha um exercício diferente do atual");
        }
        WorkoutSession session = original.getSession();
        for (SessionExercise after : sessionExerciseRepository.findAfter(session.getId(), original.getPosition())) {
            after.moveTo(after.getPosition() + 1);
        }
        SessionExercise replacement = new SessionExercise(session, exercise, original.getPosition() + 1);
        replacement.copyPlan(original.getPlannedExerciseId(), original.plannedSnapshot());
        sessionExerciseRepository.save(replacement);
        original.substitutedBy(replacement);
        return views.session(session);
    }

    @Transactional
    public SessionExerciseResponse updateNotes(UUID userId, UUID sessionExerciseId, String notes) {
        SessionExercise exercise = lookup.sessionExercise(userId, sessionExerciseId);
        exercise.updateNotes(notes == null || notes.isBlank() ? null : notes.trim());
        return views.exercise(exercise);
    }

    // ------------------------------------------------------------------------------- apoio

    private void refreshStatus(SessionExercise exercise) {
        exercise.refreshStatus(setRepository.countCompletedSets(exercise.getId()));
    }

    private SetData validate(SessionExercise exercise, SetRequest request) {
        if (request.status() == SetStatus.COMPLETED && request.reps() == null) {
            throw new InvalidFieldException("reps", "Informe as repetições da série concluída");
        }
        if (request.parentSetId() != null) {
            if (request.setType() != SetType.DROP_SET && request.setType() != SetType.REST_PAUSE) {
                throw new InvalidFieldException("parentSetId", "Só drop set e rest-pause têm série principal");
            }
            ExerciseSet parent = lookup.set(exercise.getUserId(), request.parentSetId());
            if (!parent.getSessionExercise().getId().equals(exercise.getId())) {
                throw new InvalidFieldException("parentSetId", "A série principal deve ser do mesmo exercício");
            }
        }
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();
        return new SetData(
                request.setType(), request.parentSetId(), request.status(), request.loadKg(),
                request.reps(), request.rir(), request.restSeconds(), notes);
    }
}
