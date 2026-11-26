package com.gymwork.api.workout;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkoutSessionIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
    }

    /** Plano ativo com uma fase iniciada há 8 dias (semana 2) e um dia com dois exercícios. */
    private record Fixture(String planId, String phaseId, String dayId, String firstPlanned, String bench, String row) {
    }

    private Fixture fixture(ApiClient client) throws Exception {
        String planId = read(client.post("/api/plans", "{\"name\":\"Hipertrofia\",\"goal\":\"HYPERTROPHY\"}"), "$.id");
        String phaseId = read(client.post("/api/plans/{id}/phases", """
                {"name":"Fase 1","weeksCount":4,"startDate":"%s"}
                """.formatted(TODAY.minusDays(8)), planId), "$.id");
        String dayId = read(client.post("/api/phases/{id}/days", "{\"name\":\"UPPER\"}", phaseId), "$.id");
        String bench = exerciseId(client, "supino reto com barra");
        String row = exerciseId(client, "remada curvada com barra");
        String firstPlanned = read(client.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":3,"repsMin":8,"repsMax":12,"restSeconds":90,"suggestedLoadKg":40,"targetRir":2}
                """.formatted(bench), dayId), "$.id");
        client.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":2,"repsMin":10,"repsMax":12}
                """.formatted(row), dayId).andExpect(status().isCreated());
        client.post("/api/plans/{id}/activate", null, planId).andExpect(status().isOk());
        return new Fixture(planId, phaseId, dayId, firstPlanned, bench, row);
    }

    @Test
    void sessionFromPlanCopiesThePlanAndTracksProgressBySet() throws Exception {
        Fixture f = fixture(api);

        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.planName").value("Hipertrofia"))
                .andExpect(jsonPath("$.dayName").value("UPPER"))
                .andExpect(jsonPath("$.weekNumber").value(2))
                .andExpect(jsonPath("$.plannedSets").value(5))
                .andExpect(jsonPath("$.completedSets").value(0))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Supino reto com barra"))
                .andExpect(jsonPath("$.exercises[0].plannedSets").value(3))
                .andExpect(jsonPath("$.exercises[0].referenceLoadKg").value(40))
                .andExpect(jsonPath("$.exercises[0].status").value("PENDING")), "$.id");
        String bench = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");

        // aquecimento não conta como série
        addSet(bench, "WARMUP", "COMPLETED", "20", 12).andExpect(jsonPath("$.status").value("PENDING"));
        addSet(bench, "WORKING", "COMPLETED", "40", 12).andExpect(jsonPath("$.status").value("PARTIAL"));
        addSet(bench, "WORKING", "COMPLETED", "40", 10);
        addSet(bench, "WORKING", "COMPLETED", "40", 9)
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.sets.length()").value(4))
                .andExpect(jsonPath("$.sets[3].setNumber").value(4));

        // mudar o plano depois não altera a sessão
        api.put("/api/planned-exercises/{id}", "{\"sets\":5,\"repsMin\":5,\"repsMax\":6}", f.firstPlanned())
                .andExpect(status().isOk());
        api.put("/api/days/{id}", "{\"name\":\"PEITO\"}", f.dayId()).andExpect(status().isOk());

        api.get("/api/sessions/current")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.dayName").value("UPPER"))
                .andExpect(jsonPath("$.exercises[0].plannedSets").value(3))
                .andExpect(jsonPath("$.exercises[0].repsMax").value(12))
                .andExpect(jsonPath("$.completedSets").value(3));

        // finalizar mesmo com o segundo exercício sem séries
        api.post("/api/sessions/{id}/finish", "{\"notes\":\"Bom treino\"}", sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishedAt").exists())
                .andExpect(jsonPath("$.notes").value("Bom treino"))
                .andExpect(jsonPath("$.exercises[1].status").value("PENDING"))
                .andExpect(jsonPath("$.exercises[1].sets.length()").value(0));
        api.get("/api/sessions/current").andExpect(status().isNoContent());
        api.post("/api/sessions/{id}/finish", null, sessionId).andExpect(status().isBadRequest());

        api.get("/api/sessions")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].completedSets").value(3))
                .andExpect(jsonPath("$.content[0].exercisesCount").value(2));
    }

    @Test
    void onlyOneSessionCanBeInProgress() throws Exception {
        String first = read(api.post("/api/sessions", "{}").andExpect(status().isCreated()), "$.id");

        api.post("/api/sessions", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sessionId").value(first));

        api.get("/api/sessions/start-options").andExpect(jsonPath("$.current.id").value(first));
    }

    @Test
    void startOptionsShowTodaysPhaseWeekAndDays() throws Exception {
        Fixture f = fixture(api);

        api.get("/api/sessions/start-options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.plan.id").value(f.planId()))
                .andExpect(jsonPath("$.phase.id").value(f.phaseId()))
                .andExpect(jsonPath("$.week.weekNumber").value(2))
                .andExpect(jsonPath("$.days[0].name").value("UPPER"))
                .andExpect(jsonPath("$.days[0].exercisesCount").value(2))
                .andExpect(jsonPath("$.days[0].lastPerformedAt").doesNotExist());

        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        api.post("/api/sessions/{id}/finish", null, sessionId).andExpect(status().isOk());
        api.get("/api/sessions/start-options").andExpect(jsonPath("$.days[0].lastPerformedAt").exists());
    }

    @Test
    void lastExecutionIgnoresWarmupsAndSetsAreIdempotentByClientId() throws Exception {
        Fixture f = fixture(api);
        String first = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String bench = read(api.get("/api/sessions/{id}", first), "$.exercises[0].id");
        addSet(bench, "WARMUP", "COMPLETED", "20", 15);
        addSet(bench, "WORKING", "COMPLETED", "42.5", 10);
        addSet(bench, "WORKING", "COMPLETED", "42.5", 8);
        api.post("/api/sessions/{id}/finish", null, first).andExpect(status().isOk());

        String second = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId()))
                .andExpect(jsonPath("$.exercises[0].lastExecution.sessionId").value(first))
                .andExpect(jsonPath("$.exercises[0].lastExecution.sets.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].lastExecution.sets[0].loadKg").value(42.5))
                .andExpect(jsonPath("$.exercises[0].lastExecution.sets[1].reps").value(8))
                // remada nunca foi feita
                .andExpect(jsonPath("$.exercises[1].lastExecution").doesNotExist()), "$.id");

        String benchNow = read(api.get("/api/sessions/{id}", second), "$.exercises[0].id");
        String clientId = UUID.randomUUID().toString();
        String body = """
                {"clientId":"%s","setType":"WORKING","status":"COMPLETED","loadKg":45,"reps":8}
                """.formatted(clientId);
        api.post("/api/session-exercises/{id}/sets", body, benchNow).andExpect(status().isOk());
        api.post("/api/session-exercises/{id}/sets", body, benchNow)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets.length()").value(1));
    }

    @Test
    void skipSubstituteAndCorrectSets() throws Exception {
        Fixture f = fixture(api);
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String bench = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        String row = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[1].id");

        api.post("/api/session-exercises/{id}/skip", null, row).andExpect(jsonPath("$.status").value("SKIPPED"));
        api.post("/api/session-exercises/{id}/unskip", null, row).andExpect(jsonPath("$.status").value("PENDING"));
        api.post("/api/session-exercises/{id}/skip", null, row);
        // registrar uma série desfaz o "pular"
        addSet(row, "WORKING", "COMPLETED", "50", 10).andExpect(jsonPath("$.status").value("PARTIAL"));

        // substituir: o original mantém a série feita, o novo entra logo depois com a mesma configuração
        String setId = read(addSet(bench, "WORKING", "COMPLETED", "40", 10), "$.sets[0].id");
        String dumbbell = exerciseId(api, "supino reto com halteres");
        api.post("/api/session-exercises/{id}/substitute", "{\"exerciseId\":\"%s\"}".formatted(dumbbell), bench)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(3))
                .andExpect(jsonPath("$.exercises[0].status").value("SUBSTITUTED"))
                .andExpect(jsonPath("$.exercises[0].sets.length()").value(1))
                .andExpect(jsonPath("$.exercises[1].exerciseName").value("Supino reto com halteres"))
                .andExpect(jsonPath("$.exercises[1].plannedSets").value(3))
                .andExpect(jsonPath("$.exercises[1].referenceLoadKg").value(40))
                .andExpect(jsonPath("$.exercises[2].exerciseName").value("Remada curvada com barra"))
                // o substituído não conta no total planejado (o novo herda as 3 séries)
                .andExpect(jsonPath("$.plannedSets").value(5));
        addSet(bench, "WORKING", "COMPLETED", "40", 10).andExpect(status().isBadRequest());

        // corrigir e remover série
        api.put("/api/sets/{id}", """
                {"setType":"WORKING","status":"INTERRUPTED","loadKg":40,"reps":6,"notes":"Ombro incomodou"}
                """, setId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[0].status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.sets[0].notes").value("Ombro incomodou"));
        api.delete("/api/sets/{id}", setId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets.length()").value(0));

        // exercício extra no fim
        api.post("/api/sessions/{id}/exercises", "{\"exerciseId\":\"%s\"}".formatted(exerciseId(api, "face pull")),
                        sessionId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(3))
                .andExpect(jsonPath("$.plannedSets").doesNotExist());
    }

    @Test
    void validatesSets() throws Exception {
        String sessionId = read(api.post("/api/sessions", "{}"), "$.id");
        String exercise = read(api.post("/api/sessions/{id}/exercises",
                "{\"exerciseId\":\"%s\"}".formatted(exerciseId(api, "cadeira extensora")), sessionId), "$.id");
        String other = read(api.post("/api/sessions/{id}/exercises",
                "{\"exerciseId\":\"%s\"}".formatted(exerciseId(api, "mesa flexora")), sessionId), "$.id");

        api.post("/api/session-exercises/{id}/sets", "{\"setType\":\"WORKING\",\"status\":\"COMPLETED\",\"loadKg\":30}",
                        exercise)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reps").exists());
        api.post("/api/session-exercises/{id}/sets", "{\"setType\":\"WORKING\",\"status\":\"COMPLETED\",\"reps\":-1,\"rir\":11}",
                        exercise)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reps").exists())
                .andExpect(jsonPath("$.errors.rir").exists());
        // série não feita pode ficar sem carga e reps
        addSet(exercise, "WORKING", "NOT_PERFORMED", null, null).andExpect(jsonPath("$.status").value("PENDING"));

        String main = read(addSet(exercise, "WORKING", "COMPLETED", "30", 12), "$.sets[1].id");
        String drop = """
                {"setType":"DROP_SET","status":"COMPLETED","loadKg":20,"reps":8,"parentSetId":"%s"}
                """.formatted(main);
        api.post("/api/session-exercises/{id}/sets", drop, exercise)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[2].parentSetId").value(main))
                // drop set não conta como série a mais
                .andExpect(jsonPath("$.status").value("DONE"));
        api.post("/api/session-exercises/{id}/sets", drop, other)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.parentSetId").exists());
        api.post("/api/session-exercises/{id}/sets", drop.replace("DROP_SET", "WORKING"), exercise)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.parentSetId").exists());

        api.get("/api/sessions/{id}", sessionId).andExpect(jsonPath("$.completedSets").value(1));
    }

    @Test
    void observationsRecordPainWithoutDiagnosis() throws Exception {
        Fixture f = fixture(api);
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String bench = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");

        api.post("/api/observations", "{\"category\":\"PAIN\",\"sessionExerciseId\":\"%s\"}".formatted(bench))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bodyRegion").exists());
        api.post("/api/observations", "{\"category\":\"SLEEP\",\"bodyRegion\":\"KNEE\",\"intensity\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bodyRegion").exists());
        api.post("/api/observations", "{\"category\":\"GENERAL\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").exists());

        String painId = read(api.post("/api/observations", """
                        {"category":"PAIN","sessionExerciseId":"%s","bodyRegion":"SHOULDER","bodySide":"RIGHT",
                         "intensity":6,"moment":"DURING","interrupted":true,"text":"Pontada na descida"}
                        """.formatted(bench))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.exerciseId").value(f.bench()))
                .andExpect(jsonPath("$.interrupted").value(true)), "$.id");
        api.post("/api/observations", "{\"category\":\"SLEEP\",\"sessionId\":\"%s\",\"intensity\":3,\"text\":\"Dormi 5h\"}"
                        .formatted(sessionId))
                .andExpect(status().isCreated());
        // observação sem sessão (ex.: no dia seguinte)
        api.post("/api/observations", "{\"category\":\"RECOVERY\",\"text\":\"Pernas doloridas\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        api.get("/api/sessions/{id}", sessionId)
                .andExpect(jsonPath("$.observations.length()").value(2))
                .andExpect(jsonPath("$.observations[0].bodyRegion").value("SHOULDER"));
        api.get("/api/observations").andExpect(jsonPath("$.totalElements").value(3));

        api.put("/api/observations/{id}", """
                {"category":"DISCOMFORT","sessionExerciseId":"%s","bodyRegion":"SHOULDER","intensity":3}
                """.formatted(bench), painId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("DISCOMFORT"));
        api.delete("/api/observations/{id}", painId).andExpect(status().isNoContent());
    }

    @Test
    void historyIsKeptWhenThePlanIsDeleted() throws Exception {
        Fixture f = fixture(api);
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String bench = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        addSet(bench, "WORKING", "COMPLETED", "40", 10);
        api.post("/api/sessions/{id}/finish", null, sessionId).andExpect(status().isOk());

        api.delete("/api/plans/{id}", f.planId()).andExpect(status().isNoContent());

        api.get("/api/sessions/{id}", sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").doesNotExist())
                .andExpect(jsonPath("$.workoutDayId").doesNotExist())
                .andExpect(jsonPath("$.planName").value("Hipertrofia"))
                .andExpect(jsonPath("$.dayName").value("UPPER"))
                .andExpect(jsonPath("$.exercises[0].plannedExerciseId").doesNotExist())
                .andExpect(jsonPath("$.exercises[0].plannedSets").value(3))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(40));
    }

    @Test
    void historyFiltersByDate() throws Exception {
        for (int i = 0; i < 3; i++) {
            String id = read(api.post("/api/sessions", "{}"), "$.id");
            api.post("/api/sessions/{id}/finish", null, id).andExpect(status().isOk());
        }
        api.get("/api/sessions?size=2")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
        api.get("/api/sessions?from=" + TODAY.plusDays(1)).andExpect(jsonPath("$.totalElements").value(0));
        api.get("/api/sessions?from=" + TODAY.minusDays(1) + "&to=" + TODAY.plusDays(1))
                .andExpect(jsonPath("$.totalElements").value(3));
        api.get("/api/sessions?from=" + TODAY + "&to=" + TODAY.minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersGetNotFoundOnEverySessionResource() throws Exception {
        Fixture f = fixture(api);
        String sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String bench = read(api.get("/api/sessions/{id}", sessionId), "$.exercises[0].id");
        String setId = read(addSet(bench, "WORKING", "COMPLETED", "40", 10), "$.sets[0].id");
        String observationId = read(api.post("/api/observations",
                "{\"category\":\"ENERGY\",\"sessionId\":\"%s\",\"intensity\":7}".formatted(sessionId)), "$.id");
        String set = "{\"setType\":\"WORKING\",\"status\":\"COMPLETED\",\"loadKg\":99,\"reps\":1}";
        String exercise = "{\"exerciseId\":\"%s\"}".formatted(f.row());

        ApiClient intruder = new ApiClient(mvc, register(uniqueEmail()));
        intruder.get("/api/sessions").andExpect(jsonPath("$.totalElements").value(0));
        intruder.get("/api/sessions/current").andExpect(status().isNoContent());
        intruder.get("/api/sessions/start-options").andExpect(jsonPath("$.plan").doesNotExist());
        intruder.get("/api/observations").andExpect(jsonPath("$.totalElements").value(0));

        intruder.get("/api/sessions/{id}", sessionId).andExpect(status().isNotFound());
        intruder.patch("/api/sessions/{id}", "{\"notes\":\"x\"}", sessionId).andExpect(status().isNotFound());
        intruder.post("/api/sessions/{id}/exercises", exercise, sessionId).andExpect(status().isNotFound());
        intruder.post("/api/sessions/{id}/finish", null, sessionId).andExpect(status().isNotFound());
        intruder.delete("/api/sessions/{id}", sessionId).andExpect(status().isNotFound());

        intruder.post("/api/session-exercises/{id}/sets", set, bench).andExpect(status().isNotFound());
        intruder.post("/api/session-exercises/{id}/skip", null, bench).andExpect(status().isNotFound());
        intruder.post("/api/session-exercises/{id}/unskip", null, bench).andExpect(status().isNotFound());
        intruder.post("/api/session-exercises/{id}/substitute", exercise, bench).andExpect(status().isNotFound());
        intruder.patch("/api/session-exercises/{id}", "{\"notes\":\"x\"}", bench).andExpect(status().isNotFound());

        intruder.put("/api/sets/{id}", set, setId).andExpect(status().isNotFound());
        intruder.delete("/api/sets/{id}", setId).andExpect(status().isNotFound());

        intruder.put("/api/observations/{id}", "{\"category\":\"ENERGY\"}", observationId)
                .andExpect(status().isNotFound());
        intruder.delete("/api/observations/{id}", observationId).andExpect(status().isNotFound());
        // não consegue pendurar observação na sessão de outro
        intruder.post("/api/observations", "{\"category\":\"ENERGY\",\"sessionId\":\"%s\"}".formatted(sessionId))
                .andExpect(status().isBadRequest());
        intruder.post("/api/observations", """
                        {"category":"PAIN","sessionExerciseId":"%s","bodyRegion":"KNEE"}
                        """.formatted(bench))
                .andExpect(status().isBadRequest());
        // nem iniciar sessão com o dia de treino de outro
        intruder.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())).andExpect(status().isNotFound());

        // nada mudou para o dono
        api.get("/api/sessions/{id}", sessionId)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(40))
                .andExpect(jsonPath("$.observations.length()").value(1));
    }

    @Test
    void deletingAccountRemovesSessionsAndObservations() throws Exception {
        String email = uniqueEmail();
        ApiClient owner = new ApiClient(mvc, register(email));
        Fixture f = fixture(owner);
        String custom = read(owner.post("/api/exercises", """
                {"name":"Meu exercício","primaryMuscleGroup":"CHEST","type":"ISOLATION","equipment":"CABLE"}
                """), "$.id");
        String sessionId = read(owner.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(f.dayId())), "$.id");
        String extra = read(owner.post("/api/sessions/{id}/exercises", "{\"exerciseId\":\"%s\"}".formatted(custom),
                sessionId), "$.id");
        owner.post("/api/session-exercises/{id}/sets", "{\"setType\":\"WORKING\",\"status\":\"COMPLETED\",\"reps\":10}",
                extra).andExpect(status().isOk());
        owner.post("/api/observations", "{\"category\":\"PAIN\",\"sessionExerciseId\":\"%s\",\"bodyRegion\":\"ELBOW\"}"
                .formatted(extra)).andExpect(status().isCreated());
        owner.post("/api/observations", "{\"category\":\"RECOVERY\",\"exerciseId\":\"%s\",\"text\":\"ok\"}"
                .formatted(custom)).andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);

        owner.deleteJson("/api/me", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());

        for (String table : List.of("workout_sessions", "session_exercises", "exercise_sets", "workout_observations")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId))
                    .as(table).isZero();
        }
    }

    // ------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions addSet(
            String sessionExerciseId, String type, String status, String load, Integer reps) throws Exception {
        String body = "{\"setType\":\"%s\",\"status\":\"%s\"%s%s}".formatted(
                type, status,
                load == null ? "" : ",\"loadKg\":" + load,
                reps == null ? "" : ",\"reps\":" + reps);
        return api.post("/api/session-exercises/{id}/sets", body, sessionExerciseId);
    }

    private static String exerciseId(ApiClient client, String search) throws Exception {
        return read(client.get("/api/exercises?scope=GLOBAL&search=" + search), "$.content[0].id");
    }
}
