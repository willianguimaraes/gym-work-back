package com.gymwork.api.training;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class TrainingPlanIntegrationTest extends IntegrationTest {

    private static final String PLAN = """
            {"name":"Hipertrofia 2026","goal":"HYPERTROPHY","startDate":"2026-01-05","notes":"Foco em ombros"}
            """;
    private static final String PHASE = """
            {"name":"Fase 4 — Ombros e Peito Superior","startDate":"2026-01-05","weeksCount":8}
            """;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
    }

    @Test
    void buildsPlanWithPhaseWeeksDaysAndExercises() throws Exception {
        String planId = read(api.post("/api/plans", PLAN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")), "$.id");

        String phaseId = read(api.post("/api/plans/{id}/phases", PHASE, planId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weeks.length()").value(8))
                .andExpect(jsonPath("$.weeks[0].startDate").value("2026-01-05"))
                .andExpect(jsonPath("$.weeks[7].weekNumber").value(8))
                .andExpect(jsonPath("$.weeks[7].startDate").value("2026-02-23"))
                .andExpect(jsonPath("$.endDate").value("2026-03-01")), "$.id");

        String upper = createDay(phaseId, "UPPER", "MONDAY");
        String lower = createDay(phaseId, "LOWER A", "TUESDAY");

        String incline = exerciseId("supino inclinado maquina");
        String lateral = exerciseId("elevacao lateral com halteres");
        String first = read(api.post("/api/days/{id}/exercises", """
                        {"exerciseId":"%s","sets":3,"repsMin":6,"repsMax":10,"restSeconds":120,
                         "suggestedLoadKg":45.5,"targetRir":2,"notes":"Controlar a descida"}
                        """.formatted(incline), upper)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercise.name").value("Supino inclinado máquina"))
                .andExpect(jsonPath("$.suggestedLoadKg").value(45.5)), "$.id");
        String second = addExercise(upper, lateral);

        // reordenar exercícios e dias
        api.put("/api/days/{id}/exercises/order", ids(second, first), upper)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].id").value(second))
                .andExpect(jsonPath("$.exercises[1].id").value(first));
        api.put("/api/phases/{id}/days/order", ids(lower, upper), phaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].name").value("LOWER A"))
                .andExpect(jsonPath("$.days[1].exercises.length()").value(2));

        // substituir mantém a configuração
        String flat = exerciseId("supino reto com halteres");
        api.post("/api/planned-exercises/{id}/replace", "{\"exerciseId\":\"%s\"}".formatted(flat), first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercise.name").value("Supino reto com halteres"))
                .andExpect(jsonPath("$.sets").value(3))
                .andExpect(jsonPath("$.repsMax").value(10))
                .andExpect(jsonPath("$.restSeconds").value(120));

        api.get("/api/plans/{id}", planId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeeks").value(8))
                .andExpect(jsonPath("$.phases[0].daysCount").value(2));
        api.get("/api/plans")
                .andExpect(jsonPath("$[0].phasesCount").value(1))
                .andExpect(jsonPath("$[0].totalWeeks").value(8));

        api.delete("/api/planned-exercises/{id}", second).andExpect(status().isNoContent());
        api.get("/api/days/{id}", upper)
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.phaseName").value("Fase 4 — Ombros e Peito Superior"));
    }

    @Test
    void changingPhaseDurationAddsOrRemovesWeeksAtTheEnd() throws Exception {
        String planId = read(api.post("/api/plans", PLAN), "$.id");
        String phaseId = read(api.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
        String week2 = read(api.get("/api/phases/{id}", phaseId), "$.weeks[1].id");

        api.put("/api/weeks/{id}", "{\"deload\":true,\"notes\":\"Semana leve\"}", week2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deload").value(true));

        api.put("/api/phases/{id}", "{\"name\":\"Fase curta\",\"weeksCount\":3}", phaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks.length()").value(3))
                .andExpect(jsonPath("$.weeks[1].deload").value(true))
                // sem data de início, as semanas ficam sem data
                .andExpect(jsonPath("$.weeks[0].startDate").doesNotExist());

        api.put("/api/phases/{id}", "{\"name\":\"Fase longa\",\"weeksCount\":5,\"startDate\":\"2026-03-02\"}", phaseId)
                .andExpect(jsonPath("$.weeks.length()").value(5))
                .andExpect(jsonPath("$.weeks[4].startDate").value("2026-03-30"))
                .andExpect(jsonPath("$.weeks[1].id").value(week2));
    }

    @Test
    void validatesPlannedExerciseConfiguration() throws Exception {
        String day = createDay(newPhase(), "PUSH", null);
        String exercise = exerciseId("desenvolvimento com halteres");

        api.post("/api/days/{id}/exercises", """
                        {"exerciseId":"%s","sets":3,"repsMin":12,"repsMax":8}
                        """.formatted(exercise), day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.repsMax").exists());
        api.post("/api/days/{id}/exercises", "{\"sets\":0,\"repsMin\":8,\"repsMax\":12,\"targetRir\":11}", day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sets").exists())
                .andExpect(jsonPath("$.errors.targetRir").exists());
        api.post("/api/days/{id}/exercises", "{\"sets\":3,\"repsMin\":8,\"repsMax\":12}", day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exerciseId").exists());
        api.post("/api/days/{id}/exercises", """
                        {"exerciseId":"%s","sets":3,"repsMin":8,"repsMax":12}
                        """.formatted(UUID.randomUUID()), day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exerciseId").exists());
        api.post("/api/plans", "{\"name\":\"X\",\"goal\":\"STRENGTH\",\"startDate\":\"2026-05-01\","
                        + "\"expectedEndDate\":\"2026-04-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expectedEndDate").exists());
    }

    @Test
    void reorderRequiresExactlyTheCurrentItems() throws Exception {
        String phase = newPhase();
        String a = createDay(phase, "A", null);
        String b = createDay(phase, "B", null);

        api.put("/api/phases/{id}/days/order", ids(a), phase).andExpect(status().isBadRequest());
        api.put("/api/phases/{id}/days/order", ids(a, a), phase).andExpect(status().isBadRequest());
        api.put("/api/phases/{id}/days/order", ids(a, UUID.randomUUID().toString()), phase)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ids").exists());
        api.put("/api/phases/{id}/days/order", ids(b, a), phase).andExpect(status().isOk());
    }

    @Test
    void onlyOnePlanIsActiveAtATime() throws Exception {
        String first = read(api.post("/api/plans", PLAN.replace("2026\"", "A\"")), "$.id");
        String second = read(api.post("/api/plans", PLAN.replace("2026\"", "B\"")), "$.id");

        api.post("/api/plans/{id}/activate", null, first).andExpect(jsonPath("$.status").value("ACTIVE"));
        api.post("/api/plans/{id}/activate", null, second).andExpect(jsonPath("$.status").value("ACTIVE"));
        api.get("/api/plans/{id}", first).andExpect(jsonPath("$.status").value("COMPLETED"));

        // ativar pela edição tem o mesmo efeito
        api.put("/api/plans/{id}", PLAN.replace("}", ",\"status\":\"ACTIVE\"}"), first)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        api.get("/api/plans/{id}", second).andExpect(jsonPath("$.status").value("COMPLETED"));

        List<String> active = read(api.get("/api/plans"), "$[?(@.status == 'ACTIVE')].id");
        assertThat(active).containsExactly(first);
    }

    @Test
    void duplicatingPlanCopiesStructureWithoutTouchingTheOriginal() throws Exception {
        String planId = read(api.post("/api/plans", PLAN), "$.id");
        String phaseId = read(api.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
        String week8 = read(api.get("/api/phases/{id}", phaseId), "$.weeks[7].id");
        api.put("/api/weeks/{id}", "{\"deload\":true}", week8).andExpect(status().isOk());
        String day = createDay(phaseId, "UPPER", "MONDAY");
        addExercise(day, exerciseId("puxada neutra"));
        addExercise(day, exerciseId("remada articulada"));
        api.post("/api/plans/{id}/activate", null, planId).andExpect(status().isOk());

        String copyId = read(api.post("/api/plans/{id}/duplicate", null, planId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hipertrofia 2026 (cópia)"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.totalWeeks").value(8))
                .andExpect(jsonPath("$.phases[0].daysCount").value(1)), "$.id");

        String copiedPhase = read(api.get("/api/plans/{id}", copyId), "$.phases[0].id");
        assertThat(copiedPhase).isNotEqualTo(phaseId);
        api.get("/api/phases/{id}", copiedPhase)
                .andExpect(jsonPath("$.weeks[7].deload").value(true))
                .andExpect(jsonPath("$.weeks[0].startDate").doesNotExist())
                .andExpect(jsonPath("$.days[0].name").value("UPPER"))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.name").value("Puxada neutra"))
                .andExpect(jsonPath("$.days[0].exercises[1].exercise.name").value("Remada articulada"));

        // apagar a cópia não afeta o original
        api.delete("/api/plans/{id}", copyId).andExpect(status().isNoContent());
        api.get("/api/plans/{id}", copyId).andExpect(status().isNotFound());
        api.get("/api/phases/{id}", phaseId).andExpect(jsonPath("$.days[0].exercises.length()").value(2));
    }

    @Test
    void duplicatingPhaseAppendsCopyToTheSamePlan() throws Exception {
        String planId = read(api.post("/api/plans", PLAN), "$.id");
        String phaseId = read(api.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
        addExercise(createDay(phaseId, "PULL", "THURSDAY"), exerciseId("face pull"));

        api.post("/api/phases/{id}/duplicate", null, phaseId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.name").value("Fase 4 — Ombros e Peito Superior (cópia)"))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.name").value("Face pull"));

        api.get("/api/plans/{id}", planId)
                .andExpect(jsonPath("$.phases.length()").value(2))
                .andExpect(jsonPath("$.totalWeeks").value(16));
    }

    @Test
    void otherUsersGetNotFoundOnEveryPlanResource() throws Exception {
        String planId = read(api.post("/api/plans", PLAN), "$.id");
        String phaseId = read(api.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
        String weekId = read(api.get("/api/phases/{id}", phaseId), "$.weeks[0].id");
        String dayId = createDay(phaseId, "UPPER", null);
        String plannedId = addExercise(dayId, exerciseId("barra fixa"));
        String planned = "{\"exerciseId\":\"%s\",\"sets\":3,\"repsMin\":6,\"repsMax\":8}"
                .formatted(exerciseId("barra fixa"));

        ApiClient intruder = new ApiClient(mvc, register(uniqueEmail()));
        intruder.get("/api/plans").andExpect(jsonPath("$.length()").value(0));

        intruder.get("/api/plans/{id}", planId).andExpect(status().isNotFound());
        intruder.put("/api/plans/{id}", PLAN, planId).andExpect(status().isNotFound());
        intruder.post("/api/plans/{id}/activate", null, planId).andExpect(status().isNotFound());
        intruder.post("/api/plans/{id}/duplicate", null, planId).andExpect(status().isNotFound());
        intruder.post("/api/plans/{id}/phases", PHASE, planId).andExpect(status().isNotFound());
        intruder.delete("/api/plans/{id}", planId).andExpect(status().isNotFound());

        intruder.get("/api/phases/{id}", phaseId).andExpect(status().isNotFound());
        intruder.put("/api/phases/{id}", PHASE, phaseId).andExpect(status().isNotFound());
        intruder.post("/api/phases/{id}/duplicate", null, phaseId).andExpect(status().isNotFound());
        intruder.post("/api/phases/{id}/days", "{\"name\":\"X\"}", phaseId).andExpect(status().isNotFound());
        intruder.put("/api/phases/{id}/days/order", ids(dayId), phaseId).andExpect(status().isNotFound());
        intruder.delete("/api/phases/{id}", phaseId).andExpect(status().isNotFound());

        intruder.put("/api/weeks/{id}", "{\"deload\":true}", weekId).andExpect(status().isNotFound());

        intruder.get("/api/days/{id}", dayId).andExpect(status().isNotFound());
        intruder.put("/api/days/{id}", "{\"name\":\"X\"}", dayId).andExpect(status().isNotFound());
        intruder.post("/api/days/{id}/exercises", planned, dayId).andExpect(status().isNotFound());
        intruder.put("/api/days/{id}/exercises/order", ids(plannedId), dayId).andExpect(status().isNotFound());
        intruder.delete("/api/days/{id}", dayId).andExpect(status().isNotFound());

        intruder.put("/api/planned-exercises/{id}", planned, plannedId).andExpect(status().isNotFound());
        intruder.post("/api/planned-exercises/{id}/replace", planned, plannedId).andExpect(status().isNotFound());
        intruder.delete("/api/planned-exercises/{id}", plannedId).andExpect(status().isNotFound());

        // nada mudou para o dono
        api.get("/api/phases/{id}", phaseId)
                .andExpect(jsonPath("$.name").value("Fase 4 — Ombros e Peito Superior"))
                .andExpect(jsonPath("$.weeks[0].deload").value(false))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].exercises.length()").value(1));
    }

    @Test
    void cannotPlanWithAnotherUsersCustomExercise() throws Exception {
        ApiClient other = new ApiClient(mvc, register(uniqueEmail()));
        String foreign = read(other.post("/api/exercises", """
                {"name":"Exercício secreto","primaryMuscleGroup":"BACK","type":"COMPOUND","equipment":"OTHER"}
                """), "$.id");
        String day = createDay(newPhase(), "UPPER", null);

        api.post("/api/days/{id}/exercises", """
                        {"exerciseId":"%s","sets":3,"repsMin":6,"repsMax":8}
                        """.formatted(foreign), day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exerciseId").exists());
    }

    @Test
    void databaseRejectsChildRowsPointingToAnotherUsersParent() throws Exception {
        String phaseId = newPhase();
        String otherEmail = uniqueEmail();
        register(otherEmail);
        UUID ownerId = jdbc.queryForObject(
                "select user_id from training_phases where id = ?", UUID.class, UUID.fromString(phaseId));
        UUID otherUser = jdbc.queryForObject("select id from users where email = ?", UUID.class, otherEmail);
        assertThat(otherUser).isNotEqualTo(ownerId);

        assertThatThrownBy(() -> jdbc.update("""
                        insert into workout_days (id, phase_id, user_id, name, position, created_at, updated_at)
                        values (?, ?, ?, 'Invasor', 0, now(), now())
                        """, UUID.randomUUID(), UUID.fromString(phaseId), otherUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAccountRemovesPlansAndCustomExercises() throws Exception {
        String email = uniqueEmail();
        ApiClient owner = new ApiClient(mvc, register(email));
        String custom = read(owner.post("/api/exercises", """
                {"name":"Meu exercício","primaryMuscleGroup":"CHEST","type":"ISOLATION","equipment":"CABLE"}
                """), "$.id");
        String planId = read(owner.post("/api/plans", PLAN), "$.id");
        String phaseId = read(owner.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
        String day = read(owner.post("/api/phases/{id}/days", "{\"name\":\"UPPER\"}", phaseId), "$.id");
        owner.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":3,"repsMin":10,"repsMax":15}
                """.formatted(custom), day).andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);

        owner.deleteJson("/api/me", "{\"password\":\"errada\"}").andExpect(status().isBadRequest());
        owner.deleteJson("/api/me", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());

        for (String table : List.of("training_plans", "training_phases", "training_weeks", "workout_days",
                "planned_exercises")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id = ?", Long.class, userId))
                .isZero();
    }

    // ------------------------------------------------------------------------------------------

    private String newPhase() throws Exception {
        String planId = read(api.post("/api/plans", PLAN), "$.id");
        return read(api.post("/api/plans/{id}/phases", PHASE, planId), "$.id");
    }

    private String createDay(String phaseId, String name, String dayOfWeek) throws Exception {
        String body = dayOfWeek == null
                ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"name\":\"%s\",\"dayOfWeek\":\"%s\"}".formatted(name, dayOfWeek);
        return read(api.post("/api/phases/{id}/days", body, phaseId).andExpect(status().isCreated()), "$.id");
    }

    private String addExercise(String dayId, String exerciseId) throws Exception {
        return read(api.post("/api/days/{id}/exercises", """
                        {"exerciseId":"%s","sets":3,"repsMin":8,"repsMax":12}
                        """.formatted(exerciseId), dayId)
                .andExpect(status().isCreated()), "$.id");
    }

    private String exerciseId(String search) throws Exception {
        return read(api.get("/api/exercises?scope=GLOBAL&search=" + search), "$.content[0].id");
    }

    private static String ids(String... ids) {
        return "{\"ids\":[" + String.join(",", List.of(ids).stream().map(id -> "\"" + id + "\"").toList()) + "]}";
    }
}
