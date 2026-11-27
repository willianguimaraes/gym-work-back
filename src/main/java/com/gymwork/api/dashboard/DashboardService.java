package com.gymwork.api.dashboard;

import com.gymwork.api.assessment.AssessmentService;
import com.gymwork.api.assessment.dto.TrendPoint;
import com.gymwork.api.progression.ProgressionAnalyzer.Trend;
import com.gymwork.api.progression.ProgressionService;
import com.gymwork.api.progression.dto.ProgressionItem;
import com.gymwork.api.progression.dto.ProgressionReport;
import com.gymwork.api.progression.dto.VolumeReport;
import com.gymwork.api.training.TrainingReader;
import com.gymwork.api.user.UserTime;
import com.gymwork.api.workout.ObservationService;
import com.gymwork.api.workout.SessionService;
import com.gymwork.api.workout.dto.StartOptionsResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a tela inicial reaproveitando os serviços de cada módulo (nenhuma regra nova). */
@Service
public class DashboardService {

    static final int TREND_POINTS = 20;
    static final int WEEKS = 8;
    private static final int RECENT_SESSIONS = 3;
    private static final int RECENT_OBSERVATIONS = 5;
    private static final int OBSERVATION_DAYS = 30;
    private static final int ITEMS_PER_GROUP = 5;

    private final SessionService sessionService;
    private final ObservationService observationService;
    private final ProgressionService progressionService;
    private final AssessmentService assessmentService;
    private final TrainingReader trainingReader;
    private final UserTime userTime;

    public DashboardService(
            SessionService sessionService,
            ObservationService observationService,
            ProgressionService progressionService,
            AssessmentService assessmentService,
            TrainingReader trainingReader,
            UserTime userTime) {
        this.sessionService = sessionService;
        this.observationService = observationService;
        this.progressionService = progressionService;
        this.assessmentService = assessmentService;
        this.trainingReader = trainingReader;
        this.userTime = userTime;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId) {
        StartOptionsResponse options = sessionService.startOptions(userId, null);
        var today = options.today();

        DashboardResponse.Plan plan = options.plan() == null
                ? null
                : new DashboardResponse.Plan(
                        options.plan().id(), options.plan().name(), trainingReader.planGoal(userId, options.plan().id()));
        DashboardResponse.Phase phase = options.phase() == null
                ? null
                : new DashboardResponse.Phase(
                        options.phase().id(), options.phase().name(), options.phase().startDate(),
                        options.phase().endDate(), options.phase().weeksCount(), options.days().size());
        DashboardResponse.Week week = options.week() == null
                ? null
                : new DashboardResponse.Week(
                        options.week().id(), options.week().weekNumber(), options.week().deload(), options.week().startDate());
        DashboardResponse.Next next = NextWorkout.choose(options.days(), today, userTime.zone(userId))
                .map(choice -> new DashboardResponse.Next(
                        choice.day().id(), choice.day().name(), choice.day().dayOfWeek(), choice.day().exercisesCount(),
                        choice.day().lastPerformedAt(), choice.reason(), choice.date()))
                .orElse(null);

        var recent = sessionService.history(userId, null, null, PageRequest.of(0, RECENT_SESSIONS)).content();
        var observations = observationService.period(
                userId, today.minusDays(OBSERVATION_DAYS - 1L), today, PageRequest.of(0, RECENT_OBSERVATIONS)).content();

        VolumeReport volume = progressionService.volume(userId, WEEKS);
        List<DashboardResponse.WeekCount> weeks = volume.weeks().stream()
                .map(w -> new DashboardResponse.WeekCount(w.weekStart(), w.sessions(), w.validSets()))
                .toList();
        int thisWeek = weeks.isEmpty() ? 0 : weeks.get(weeks.size() - 1).sessions();
        DashboardResponse.Frequency frequency = new DashboardResponse.Frequency(
                phase == null ? 0 : phase.daysCount(), thisWeek, weeks);

        ProgressionReport report = progressionService.report(userId, null, WEEKS);
        DashboardResponse.Progression progression = new DashboardResponse.Progression(
                WEEKS,
                report.counts(),
                items(report, Trend.PROGRESSING),
                items(report, Trend.STABLE),
                items(report, Trend.REGRESSING),
                report.items().stream()
                        .sorted(Comparator.comparingInt(ProgressionItem::executions).reversed()
                                .thenComparing(ProgressionItem::exerciseName, String.CASE_INSENSITIVE_ORDER))
                        .limit(ITEMS_PER_GROUP + 1L)
                        .map(DashboardService::item)
                        .toList());

        return new DashboardResponse(
                today, plan, phase, week, next, options.current(), recent, body(userId), frequency, progression,
                volume.totals(), observations);
    }

    private DashboardResponse.Body body(UUID userId) {
        List<TrendPoint> trend = assessmentService.trend(userId);
        if (trend.isEmpty()) {
            return null;
        }
        List<TrendPoint> withWeight = trend.stream().filter(p -> p.weightKg() != null).toList();
        TrendPoint latestWeight = withWeight.isEmpty() ? null : withWeight.get(withWeight.size() - 1);
        TrendPoint previousWeight = withWeight.size() < 2 ? null : withWeight.get(withWeight.size() - 2);
        TrendPoint last = trend.get(trend.size() - 1);
        List<TrendPoint> recent = trend.subList(Math.max(0, trend.size() - TREND_POINTS), trend.size());
        return new DashboardResponse.Body(
                latestWeight == null ? null : latestWeight.weightKg(),
                latestWeight == null ? null : latestWeight.assessedOn(),
                previousWeight == null ? null : previousWeight.weightKg(),
                previousWeight == null ? null : previousWeight.assessedOn(),
                last.assessmentId(),
                last.assessedOn(),
                List.copyOf(recent));
    }

    private static List<DashboardResponse.ExerciseItem> items(ProgressionReport report, Trend trend) {
        return report.items().stream()
                .filter(item -> item.analysis().trend() == trend)
                .limit(ITEMS_PER_GROUP)
                .map(DashboardService::item)
                .toList();
    }

    private static DashboardResponse.ExerciseItem item(ProgressionItem item) {
        return new DashboardResponse.ExerciseItem(
                item.exerciseId(), item.exerciseName(), item.executions(), item.analysis().latestSummary(),
                item.analysis().trendDetail());
    }
}
