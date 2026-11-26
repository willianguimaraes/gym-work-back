package com.gymwork.api.progression;

import static com.gymwork.api.support.ApiClient.read;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgressionIntegrationTest extends IntegrationTest {

    private ApiClient api;
    private String bench;
    private String dayId;
    private String phaseId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
        bench = read(api.get("/api/exercises?scope=GLOBAL&search=supino reto com barra"), "$.content[0].id");
        String planId = read(api.post("/api/plans", "{\"name\":\"Força\",\"goal\":\"STRENGTH\"}"), "$.id");
        phaseId = read(api.post("/api/plans/{id}/phases", "{\"name\":\"Fase 1\",\"weeksCount\":4}", planId), "$.id");
        dayId = read(api.post("/api/phases/{id}/days", "{\"name\":\"PUSH\"}", phaseId), "$.id");
        api.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":3,"repsMin":6,"repsMax":8,"restSeconds":120,"targetRir":1}
                """.formatted(bench), dayId).andExpect(status().isCreated());
    }

    /** Faz uma sessão do dia com as séries do supino (carga, reps...) e finaliza. */
    private String workout(String load, int... reps) throws Exception {
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(dayId)), "$.id");
        String exercise = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        api.post("/api/session-exercises/{id}/sets",
                "{\"setType\":\"WARMUP\",\"status\":\"COMPLETED\",\"loadKg\":20,\"reps\":12}", exercise);
        for (int r : reps) {
            api.post("/api/session-exercises/{id}/sets", """
                    {"setType":"WORKING","status":"COMPLETED","loadKg":%s,"reps":%d,"rir":1}
                    """.formatted(load, r), exercise).andExpect(status().isOk());
        }
        api.post("/api/sessions/{id}/finish", null, sessionId).andExpect(status().isOk());
        return sessionId;
    }

    @Test
    void exerciseHistorySummaryAndChart() throws Exception {
        workout("60", 6, 6, 6);
        workout("60", 7, 7, 6);
        String last = workout("60", 8, 8, 8);
        // sessão em andamento não entra na evolução
        api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(dayId)).andExpect(status().isCreated());

        api.get("/api/exercises/{id}/history?size=2", bench)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].sessionId").value(last))
                .andExpect(jsonPath("$.content[0].summary").value("60 kg — 8/8/8"))
                .andExpect(jsonPath("$.content[0].validSets").value(3))
                .andExpect(jsonPath("$.content[0].complete").value(true))
                // volume sem o aquecimento: 60 × 24
                .andExpect(jsonPath("$.content[0].volumeKg").value(1440))
                .andExpect(jsonPath("$.content[0].sets.length()").value(4))
                .andExpect(jsonPath("$.content[1].summary").value("60 kg — 7/7/6"));

        api.get("/api/exercises/{id}/summary", bench)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").value(3))
                .andExpect(jsonPath("$.lastSummary").value("60 kg — 8/8/8"))
                .andExpect(jsonPath("$.lastReps.length()").value(3))
                .andExpect(jsonPath("$.bestSet.loadKg").value(60))
                .andExpect(jsonPath("$.bestSet.reps").value(8))
                .andExpect(jsonPath("$.analysis.trend").value("PROGRESSING"))
                .andExpect(jsonPath("$.analysis.suggestion").value("INCREASE_LOAD"))
                .andExpect(jsonPath("$.analysis.suggestedLoadKg").value(62.5))
                .andExpect(jsonPath("$.rule.custom").value(false));

        api.get("/api/exercises/{id}/progress", bench)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].repsAtTopLoad").value(6.0))
                .andExpect(jsonPath("$[2].repsAtTopLoad").value(8.0))
                .andExpect(jsonPath("$[2].topLoadKg").value(60));
        api.get("/api/exercises/{id}/progress?groupBy=PHASE", bench)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Fase 1"))
                .andExpect(jsonPath("$[0].executions").value(3));
        api.get("/api/exercises/{id}/progress?groupBy=WEEK", bench)
                .andExpect(jsonPath("$[0].label").value(org.hamcrest.Matchers.startsWith("Sem. ")));
        api.get("/api/exercises/{id}/progress?groupBy=MONTH", bench).andExpect(status().isBadRequest());
    }

    @Test
    void painSuspendsTheIncreaseSuggestion() throws Exception {
        workout("60", 7, 7, 7);
        String sessionId = workout("60", 8, 8, 8);
        String exercise = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        api.post("/api/observations", """
                {"category":"PAIN","sessionExerciseId":"%s","bodyRegion":"SHOULDER","intensity":4}
                """.formatted(exercise)).andExpect(status().isCreated());

        api.get("/api/exercises/{id}/summary", bench)
                .andExpect(jsonPath("$.analysis.suggestion").value("HOLD_PAIN"))
                .andExpect(jsonPath("$.analysis.suggestedLoadKg").doesNotExist())
                .andExpect(jsonPath("$.observations.length()").value(1));
    }

    @Test
    void progressionRuleCanBeCustomizedAndReset() throws Exception {
        workout("60", 7, 7, 7);
        workout("60", 8, 8, 8);

        api.get("/api/exercises/{id}/progression-rule", bench)
                .andExpect(jsonPath("$.custom").value(false))
                .andExpect(jsonPath("$.sessionsAtTop").value(1))
                .andExpect(jsonPath("$.effectiveIncrementKg").value(2.5));

        api.put("/api/exercises/{id}/progression-rule",
                        "{\"sessionsAtTop\":2,\"loadIncrementKg\":5,\"enabled\":true}", bench)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.effectiveIncrementKg").value(5));
        api.get("/api/exercises/{id}/summary", bench)
                .andExpect(jsonPath("$.analysis.suggestion").value("CONFIRM_TOP"));

        workout("60", 8, 8, 8);
        api.get("/api/exercises/{id}/summary", bench)
                .andExpect(jsonPath("$.analysis.suggestion").value("INCREASE_LOAD"))
                .andExpect(jsonPath("$.analysis.suggestedLoadKg").value(65));

        api.put("/api/exercises/{id}/progression-rule", "{\"sessionsAtTop\":9,\"enabled\":true}", bench)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sessionsAtTop").exists());

        api.delete("/api/exercises/{id}/progression-rule", bench)
                .andExpect(jsonPath("$.custom").value(false));
        api.get("/api/exercises/{id}/progression-rule", bench).andExpect(jsonPath("$.custom").value(false));
    }

    @Test
    void reportClassifiesExercisesByPhaseOrPeriod() throws Exception {
        workout("60", 7, 7, 7);
        workout("60", 8, 7, 7);

        api.get("/api/progression")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].exerciseName").value("Supino reto com barra"))
                .andExpect(jsonPath("$.items[0].executions").value(2))
                .andExpect(jsonPath("$.items[0].analysis.trend").value("PROGRESSING"))
                .andExpect(jsonPath("$.items[0].analysis.suggestion").value("ADD_REPS"))
                .andExpect(jsonPath("$.counts.PROGRESSING").value(1));

        api.get("/api/progression?phaseId={id}", phaseId)
                .andExpect(jsonPath("$.phaseName").value("Fase 1"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void weeklyVolumeCountsValidSetsByMuscleGroup() throws Exception {
        workout("60", 8, 8, 8);
        workout("60", 8, 8);

        api.get("/api/progression/volume?weeks=4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks.length()").value(4))
                .andExpect(jsonPath("$.weeks[3].sessions").value(2))
                .andExpect(jsonPath("$.weeks[3].validSets").value(5))
                .andExpect(jsonPath("$.weeks[3].muscles[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.weeks[3].muscles[0].sets").value(5))
                .andExpect(jsonPath("$.totals[?(@.muscleGroup == 'TRICEPS')].secondarySets").value(5))
                .andExpect(jsonPath("$.weeks[0].validSets").value(0));
    }

    @Test
    void evolutionIsIsolatedPerUser() throws Exception {
        workout("60", 8, 8, 8);
        String custom = read(api.post("/api/exercises", """
                {"name":"Supino secreto","primaryMuscleGroup":"CHEST","type":"COMPOUND","equipment":"MACHINE"}
                """), "$.id");

        ApiClient intruder = new ApiClient(mvc, register(uniqueEmail()));
        // exercício global: o intruso só vê o histórico dele (vazio)
        intruder.get("/api/exercises/{id}/history", bench).andExpect(jsonPath("$.totalElements").value(0));
        intruder.get("/api/exercises/{id}/summary", bench)
                .andExpect(jsonPath("$.executions").value(0))
                .andExpect(jsonPath("$.analysis.trend").value("INSUFFICIENT_DATA"));
        intruder.get("/api/exercises/{id}/progress", bench).andExpect(jsonPath("$.length()").value(0));
        intruder.get("/api/progression").andExpect(jsonPath("$.items.length()").value(0));
        intruder.get("/api/progression/volume").andExpect(jsonPath("$.totals.length()").value(0));
        intruder.get("/api/progression?phaseId={id}", phaseId).andExpect(status().isNotFound());

        // exercício personalizado de outro usuário: 404
        intruder.get("/api/exercises/{id}/history", custom).andExpect(status().isNotFound());
        intruder.get("/api/exercises/{id}/summary", custom).andExpect(status().isNotFound());
        intruder.get("/api/exercises/{id}/progress", custom).andExpect(status().isNotFound());
        intruder.get("/api/exercises/{id}/progression-rule", custom).andExpect(status().isNotFound());
        intruder.put("/api/exercises/{id}/progression-rule", "{\"sessionsAtTop\":2,\"enabled\":true}", custom)
                .andExpect(status().isNotFound());
        intruder.delete("/api/exercises/{id}/progression-rule", custom).andExpect(status().isNotFound());

        // a regra de um usuário não afeta o outro
        intruder.put("/api/exercises/{id}/progression-rule", "{\"sessionsAtTop\":3,\"enabled\":false}", bench)
                .andExpect(status().isOk());
        api.get("/api/exercises/{id}/progression-rule", bench).andExpect(jsonPath("$.custom").value(false));
    }
}
