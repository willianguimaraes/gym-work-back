package com.gymwork.api.training;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class PlanImportIntegrationTest extends IntegrationTest {

    /** Como costuma voltar do chat: texto em volta, cerca ```json e um nome escrito diferente do catálogo. */
    private static final String ANSWER = """
            Aqui está a próxima fase:
            ```json
            {
              "format": "gymwork.plan/v1",
              "plan": { "name": "Hipertrofia 2027", "goal": "HYPERTROPHY", "notes": "Gerado pela IA" },
              "phases": [{
                "name": "Fase 5 — Volume", "weeks": 4, "deloadWeeks": [4], "notes": null,
                "days": [
                  {
                    "name": "UPPER", "dayOfWeek": "MONDAY", "notes": null,
                    "exercises": [
                      { "exercise": "supino inclinado maquina", "sets": 4, "repsMin": 6, "repsMax": 10,
                        "restSeconds": 120, "targetRir": 2, "suggestedLoadKg": 45.5, "notes": "Pausa de 1 s" },
                      { "exercise": "Elevação lateral halteres", "sets": 3, "repsMin": 12, "repsMax": 15,
                        "restSeconds": 60, "targetRir": 1, "suggestedLoadKg": null, "notes": null }
                    ]
                  },
                  {
                    "name": "LOWER", "dayOfWeek": "TUESDAY", "notes": null,
                    "exercises": [
                      { "exercise": "Agachamento livre", "sets": 4, "repsMin": 5, "repsMax": 8,
                        "restSeconds": 180, "targetRir": 2, "suggestedLoadKg": 100, "notes": null }
                    ]
                  }
                ]
              }]
            }
            ```
            """;

    @Autowired
    private JsonMapper jsonMapper;

    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
    }

    @Test
    void previewsResolvesTheMissingExerciseAndCreatesADraftPlan() throws Exception {
        // pré-visualização: nada é gravado; um nome casou sem acento, outro precisa de escolha
        var preview = api.post("/api/plans/import/preview", body(ANSWER, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(jsonPath("$.template.plan.name").value("Hipertrofia 2027"))
                .andExpect(jsonPath("$.template.phases[0].days.length()").value(2))
                .andExpect(jsonPath("$.targetPlan").doesNotExist())
                .andExpect(jsonPath("$.exercises[0].name").value("supino inclinado maquina"))
                .andExpect(jsonPath("$.exercises[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.exercises[0].exercise.name").value("Supino inclinado máquina"))
                .andExpect(jsonPath("$.exercises[1].status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.exercises[1].candidates[0].name").value("Elevação lateral com halteres"))
                .andExpect(jsonPath("$.exercises[2].status").value("MATCHED"));
        String lateralId = read(preview, "$.exercises[1].candidates[0].id");
        api.get("/api/plans").andExpect(jsonPath("$.length()").value(0));

        // confirmar sem escolher o exercício que faltou é recusado
        api.post("/api/plans/import", body(ANSWER, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("exercises.Elevação lateral halteres")));

        api.post("/api/plans/import/preview", body(ANSWER, null, Map.of("Elevação lateral halteres", lateralId)))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.exercises[1].status").value("CHOSEN"));

        var created = api.post("/api/plans/import", body(ANSWER, null, Map.of("Elevação lateral halteres", lateralId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phaseIds.length()").value(1));
        String planId = read(created, "$.planId");
        String phaseId = read(created, "$.phaseIds[0]");

        api.get("/api/plans/{id}", planId)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.goal").value("HYPERTROPHY"))
                .andExpect(jsonPath("$.notes").value("Gerado pela IA"))
                .andExpect(jsonPath("$.startDate").doesNotExist());
        api.get("/api/phases/{id}", phaseId)
                .andExpect(jsonPath("$.weeks.length()").value(4))
                .andExpect(jsonPath("$.weeks[3].deload").value(true))
                .andExpect(jsonPath("$.weeks[0].deload").value(false))
                .andExpect(jsonPath("$.days[0].name").value("UPPER"))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.name").value("Supino inclinado máquina"))
                .andExpect(jsonPath("$.days[0].exercises[0].suggestedLoadKg").value(45.5))
                .andExpect(jsonPath("$.days[0].exercises[0].notes").value("Pausa de 1 s"))
                .andExpect(jsonPath("$.days[0].exercises[1].exercise.name").value("Elevação lateral com halteres"))
                .andExpect(jsonPath("$.days[1].exercises[0].restSeconds").value(180));

        // ida e volta: o template exportado do plano importado lê de volta sem problemas
        String exported = api.get("/api/plans/{id}/template", planId)
                .andExpect(jsonPath("$.phases[0].deloadWeeks[0]").value(4))
                .andExpect(jsonPath("$.phases[0].days[0].exercises[1].exercise").value("Elevação lateral com halteres"))
                .andReturn().getResponse().getContentAsString();
        api.post("/api/plans/import/preview", body(exported, null, null))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void invalidFileShowsEveryProblemAndCreatesNothing() throws Exception {
        String invalid = """
                {"format":"gymwork.plan/v1","plan":{"name":"X","goal":"HYPERTROPHY"},
                 "phases":[{"name":"F","weeks":4,"days":[{"name":"A","exercises":[
                   {"exercise":"Agachamento livre","sets":30,"repsMin":10,"repsMax":8}]}]}]}
                """;
        api.post("/api/plans/import/preview", body(invalid, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.template").doesNotExist())
                .andExpect(jsonPath("$.errors[0].path").value("phases[0].days[0].exercises[0].sets"))
                .andExpect(jsonPath("$.errors[1].path").value("phases[0].days[0].exercises[0].repsMax"));
        api.post("/api/plans/import", body(invalid, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("phases[0].days[0].exercises[0].sets")));
        api.post("/api/plans/import", "{\"content\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("content")));
        api.get("/api/plans").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addsPhasesToTheEndOfAnExistingPlanWithoutTouchingIt() throws Exception {
        String planId = read(api.post("/api/plans", """
                {"name":"Meu plano","goal":"STRENGTH"}
                """).andExpect(status().isCreated()), "$.id");
        String firstPhase = read(api.post("/api/plans/{id}/phases", """
                {"name":"Fase 1","weeksCount":6}
                """, planId).andExpect(status().isCreated()), "$.id");

        String phases = """
                {"format":"gymwork.plan/v1",
                 "plan":{"name":"Outro nome","goal":"HYPERTROPHY"},
                 "phases":[{"name":"Fase 2","weeks":3,"days":[{"name":"FULL","exercises":[
                   {"exercise":"Agachamento livre","sets":3,"repsMin":5,"repsMax":5}]}]}]}
                """;
        api.post("/api/plans/import/preview", body(phases, planId, null))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.targetPlan.name").value("Meu plano"))
                .andExpect(jsonPath("$.targetPlan.phases").value(1))
                .andExpect(jsonPath("$.warnings[0].path").value("plan"));
        api.post("/api/plans/import", body(phases, planId, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId));

        api.get("/api/plans/{id}", planId)
                .andExpect(jsonPath("$.name").value("Meu plano"))
                .andExpect(jsonPath("$.goal").value("STRENGTH"))
                .andExpect(jsonPath("$.phases.length()").value(2))
                .andExpect(jsonPath("$.phases[0].id").value(firstPhase))
                .andExpect(jsonPath("$.phases[1].name").value("Fase 2"));
    }

    @Test
    void sameNameWithDifferentEquipmentAsksTheUserToChoose() throws Exception {
        String custom = read(api.post("/api/exercises", """
                {"name":"Agachamento livre","primaryMuscleGroup":"QUADRICEPS","type":"COMPOUND","equipment":"SMITH"}
                """).andExpect(status().isCreated()), "$.id");
        String json = """
                {"format":"gymwork.plan/v1","plan":{"name":"P","goal":"OTHER"},
                 "phases":[{"name":"F","weeks":2,"days":[{"name":"A","exercises":[
                   {"exercise":"Agachamento livre","sets":3,"repsMin":5,"repsMax":5},
                   {"exercise":"AGACHAMENTO LIVRE","sets":2,"repsMin":8,"repsMax":8}]}]}]}
                """;
        api.post("/api/plans/import/preview", body(json, null, null))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.exercises[0].uses").value(2))
                .andExpect(jsonPath("$.exercises[0].candidates.length()").value(2));

        String phaseId = read(api.post("/api/plans/import", body(json, null, Map.of("Agachamento livre", custom)))
                .andExpect(status().isCreated()), "$.phaseIds[0]");
        api.get("/api/phases/{id}", phaseId)
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.id").value(custom))
                .andExpect(jsonPath("$.days[0].exercises[1].exercise.id").value(custom));
    }

    @Test
    void cannotImportIntoOrUseExercisesOfAnotherUser() throws Exception {
        ApiClient other = new ApiClient(mvc, register(uniqueEmail()));
        String otherPlan = read(other.post("/api/plans", """
                {"name":"Plano dela","goal":"OTHER"}
                """), "$.id");
        String otherExercise = read(other.post("/api/exercises", """
                {"name":"Exercício secreto","primaryMuscleGroup":"CHEST","type":"COMPOUND","equipment":"MACHINE"}
                """), "$.id");
        String json = """
                {"format":"gymwork.plan/v1","plan":{"name":"P","goal":"OTHER"},
                 "phases":[{"name":"F","weeks":2,"days":[{"name":"A","exercises":[
                   {"exercise":"Exercício secreto","sets":3,"repsMin":5,"repsMax":5}]}]}]}
                """;

        api.post("/api/plans/import/preview", body(json, otherPlan, null)).andExpect(status().isNotFound());
        api.post("/api/plans/import", body(json, otherPlan, null)).andExpect(status().isNotFound());

        // o nome não casa com o personalizado de outro usuário, nem escolhendo pelo ID
        api.post("/api/plans/import/preview", body(json, null, Map.of("Exercício secreto", otherExercise)))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.exercises[0].status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.exercises[0].candidates[?(@.id == '" + otherExercise + "')]").isEmpty());
        api.post("/api/plans/import", body(json, null, Map.of("Exercício secreto", otherExercise)))
                .andExpect(status().isBadRequest());

        List<Object> plans = read(other.get("/api/plans"), "$[*].id");
        assertThat(plans).containsExactly(otherPlan);
        api.get("/api/plans").andExpect(jsonPath("$.length()").value(0));
    }

    private String body(String content, String planId, Map<String, String> exercises) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("planId", planId);
        body.put("exercises", exercises);
        return jsonMapper.writeValueAsString(body);
    }
}
