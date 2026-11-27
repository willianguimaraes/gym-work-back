package com.gymwork.api.dashboard;

import static com.gymwork.api.support.ApiClient.read;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class DashboardIntegrationTest extends IntegrationTest {

    @Test
    void newUserGetsAnEmptyDashboard() throws Exception {
        ApiClient api = new ApiClient(mvc, register(uniqueEmail()));
        api.get("/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").exists())
                .andExpect(jsonPath("$.plan").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.next").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.body").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.recentSessions.length()").value(0))
                .andExpect(jsonPath("$.frequency.weeks.length()").value(8))
                .andExpect(jsonPath("$.frequency.thisWeek").value(0))
                .andExpect(jsonPath("$.progression.counts.PROGRESSING").value(0));
    }

    @Test
    void dashboardShowsPlanNextWorkoutHistoryBodyAndProgression() throws Exception {
        ApiClient api = new ApiClient(mvc, register(uniqueEmail()));
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String bench = read(api.get("/api/exercises?scope=GLOBAL&search=supino reto com barra"), "$.content[0].id");
        String planId = read(api.post("/api/plans", "{\"name\":\"Força\",\"goal\":\"STRENGTH\"}"), "$.id");
        api.post("/api/plans/{id}/activate", null, planId).andExpect(status().isOk());
        String phaseId = read(api.post("/api/plans/{id}/phases", """
                {"name":"Fase 1","startDate":"%s","weeksCount":4}
                """.formatted(today), planId), "$.id");
        String dayA = read(api.post("/api/phases/{id}/days", "{\"name\":\"A\"}", phaseId), "$.id");
        String dayB = read(api.post("/api/phases/{id}/days", "{\"name\":\"B\"}", phaseId), "$.id");
        api.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":2,"repsMin":6,"repsMax":8}
                """.formatted(bench), dayA).andExpect(status().isCreated());

        for (int reps : new int[] {6, 8}) {
            String session = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(dayA)), "$.id");
            String item = read(api.get("/api/sessions/{id}", session), "$.exercises[0].id");
            for (int i = 0; i < 2; i++) {
                api.post("/api/session-exercises/{id}/sets", """
                        {"setType":"WORKING","status":"COMPLETED","loadKg":60,"reps":%d}
                        """.formatted(reps), item).andExpect(status().isOk());
            }
            api.post("/api/sessions/{id}/finish", null, session).andExpect(status().isOk());
        }
        api.post("/api/assessments", "{\"assessedOn\":\"%s\",\"weightKg\":82.5}".formatted(today.minusDays(10)))
                .andExpect(status().isCreated());
        api.post("/api/assessments", "{\"assessedOn\":\"%s\",\"weightKg\":81}".formatted(today))
                .andExpect(status().isCreated());
        api.post("/api/observations", "{\"category\":\"SLEEP\",\"text\":\"Dormi mal\"}").andExpect(status().isCreated());

        api.get("/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("Força"))
                .andExpect(jsonPath("$.plan.goal").value("STRENGTH"))
                .andExpect(jsonPath("$.phase.name").value("Fase 1"))
                .andExpect(jsonPath("$.phase.daysCount").value(2))
                .andExpect(jsonPath("$.week.number").value(1))
                // dias sem dia da semana: rodízio, depois do A vem o B
                .andExpect(jsonPath("$.next.dayId").value(dayB))
                .andExpect(jsonPath("$.next.reason").value("ROTATION"))
                .andExpect(jsonPath("$.current").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.recentSessions.length()").value(2))
                .andExpect(jsonPath("$.body.latestWeightKg").value(81))
                .andExpect(jsonPath("$.body.previousWeightKg").value(82.5))
                .andExpect(jsonPath("$.body.trend.length()").value(2))
                .andExpect(jsonPath("$.frequency.plannedPerWeek").value(2))
                .andExpect(jsonPath("$.frequency.thisWeek").value(2))
                .andExpect(jsonPath("$.progression.counts.PROGRESSING").value(1))
                .andExpect(jsonPath("$.progression.progressing[0].name").value("Supino reto com barra"))
                .andExpect(jsonPath("$.progression.mostFrequent[0].exerciseId").value(bench))
                .andExpect(jsonPath("$.muscles[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.recentObservations[0].text").value("Dormi mal"));
    }
}
