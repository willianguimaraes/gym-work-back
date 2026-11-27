package com.gymwork.api;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Caminho completo de um usuário, de ponta a ponta: cadastro, planejamento, treino, avaliação,
 * evolução, relatórios, dashboard e exclusão da conta (sem sobrar nenhum dado dele no banco).
 */
class JourneyIntegrationTest extends IntegrationTest {

    /** Tabelas com {@code user_id}: depois da exclusão da conta, nenhuma pode ter linhas do usuário. */
    private static final List<String> USER_TABLES = List.of(
            "user_profiles", "refresh_tokens", "password_reset_tokens", "exercises:owner_id", "training_plans", "training_phases", "training_weeks",
            "workout_days", "planned_exercises", "workout_sessions", "session_exercises", "exercise_sets",
            "workout_observations", "progression_rules", "body_assessments", "body_measurements", "progress_photos");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fromSignUpToAccountDeletion() throws Exception {
        ApiClient api = new ApiClient(mvc, register(uniqueEmail()));
        String userId = read(api.get("/api/me"), "$.id");
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        // planejamento com exercício personalizado
        String custom = read(api.post("/api/exercises", """
                {"name":"Remada no banco inclinado","primaryMuscleGroup":"BACK","secondaryMuscleGroups":["BICEPS"],
                 "type":"COMPOUND","equipment":"DUMBBELL"}
                """), "$.id");
        String planId = read(api.post("/api/plans", "{\"name\":\"Hipertrofia\",\"goal\":\"HYPERTROPHY\"}"), "$.id");
        api.post("/api/plans/{id}/activate", null, planId).andExpect(status().isOk());
        String phaseId = read(api.post("/api/plans/{id}/phases", """
                {"name":"Fase 1","startDate":"%s","weeksCount":6}
                """.formatted(today), planId), "$.id");
        String dayId = read(api.post("/api/phases/{id}/days", "{\"name\":\"PULL\"}", phaseId), "$.id");
        api.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":3,"repsMin":8,"repsMax":12,"suggestedLoadKg":24}
                """.formatted(custom), dayId).andExpect(status().isCreated());
        api.put("/api/exercises/{id}/progression-rule", "{\"sessionsAtTop\":2,\"enabled\":true}", custom)
                .andExpect(status().isOk());

        // treino com uma série que cai na fila offline e é reenviada (mesmo clientId)
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(dayId)), "$.id");
        String item = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        String clientId = UUID.randomUUID().toString();
        String set = """
                {"clientId":"%s","setType":"WORKING","status":"COMPLETED","loadKg":24,"reps":12}
                """.formatted(clientId);
        api.post("/api/session-exercises/{id}/sets", set, item).andExpect(status().isOk());
        api.post("/api/session-exercises/{id}/sets", set, item).andExpect(status().isOk());
        api.post("/api/session-exercises/{id}/sets",
                "{\"setType\":\"WORKING\",\"status\":\"COMPLETED\",\"loadKg\":24,\"reps\":11}", item);
        api.post("/api/observations", """
                {"category":"DISCOMFORT","sessionExerciseId":"%s","bodyRegion":"LOWER_BACK","intensity":3}
                """.formatted(item)).andExpect(status().isCreated());
        api.post("/api/sessions/{id}/finish", "{\"notes\":\"Bom treino\"}", sessionId).andExpect(status().isOk());

        api.post("/api/assessments", "{\"assessedOn\":\"%s\",\"weightKg\":90}".formatted(today))
                .andExpect(status().isCreated());

        // evolução, relatórios e dashboard enxergam tudo
        api.get("/api/exercises/{id}/summary", custom).andExpect(jsonPath("$.lastSummary").value("24 kg — 12/11"));
        String text = api.get("/api/reports/sessions/{id}", sessionId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(text).contains("Remada no banco inclinado: 24 kg | 12/11/x", "Peso: 90 kg", "Bom treino",
                "[Desconforto] Lombar, intensidade 3");
        api.get("/api/reports/phases/{id}/ai-package?format=json", phaseId)
                .andExpect(jsonPath("$.currentPlan.phases[0].days[0].exercises[0].exercise").value("Remada no banco inclinado"))
                .andExpect(jsonPath("$.catalog[?(@.name == 'Remada no banco inclinado')]").exists());
        api.get("/api/dashboard")
                .andExpect(jsonPath("$.recentSessions[0].id").value(sessionId))
                .andExpect(jsonPath("$.body.latestWeightKg").value(90))
                .andExpect(jsonPath("$.next.dayId").value(dayId));

        // exclusão da conta apaga tudo
        api.deleteJson("/api/me", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());
        UUID id = UUID.fromString(userId);
        for (String entry : USER_TABLES) {
            String[] parts = entry.split(":");
            String column = parts.length > 1 ? parts[1] : "user_id";
            Integer rows = jdbc.queryForObject(
                    "select count(*) from " + parts[0] + " where " + column + " = ?", Integer.class, id);
            assertThat(rows).as(parts[0]).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, id)).isZero();
    }
}
