package com.gymwork.api.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExerciseCatalogIntegrationTest extends IntegrationTest {

    private static final String CUSTOM = """
            {"name":"Remada Pendlay","primaryMuscleGroup":"BACK","secondaryMuscleGroups":["BICEPS","BACK"],
             "type":"COMPOUND","equipment":"BARBELL","description":"Remada explosiva a partir do chão"}
            """;

    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
    }

    @Test
    void seedProvidesMuscleGroupsAndGlobalExercises() throws Exception {
        api.get("/api/muscle-groups")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].code").value("CHEST"))
                .andExpect(jsonPath("$[0].name").value("Peito"));

        api.get("/api/exercises?scope=GLOBAL&size=200")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(70))
                .andExpect(jsonPath("$.content[0].custom").value(false));
    }

    @Test
    void searchIgnoresAccentsCaseAndWordOrder() throws Exception {
        api.get("/api/exercises?search=LATERAL elevacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Elevação lateral com halteres")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Supino reto com barra"))));

        api.get("/api/exercises?muscleGroup=CALVES")
                .andExpect(jsonPath("$.totalElements").value(3));

        api.get("/api/exercises?equipment=SMITH")
                .andExpect(jsonPath("$.content[*].equipment", not(hasItem("BARBELL"))));
    }

    @Test
    void customExerciseBelongsToTheUserAndCanBeDeactivated() throws Exception {
        String id = ApiClient.read(api.post("/api/exercises", CUSTOM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.custom").value(true))
                // o grupo principal não se repete nos secundários
                .andExpect(jsonPath("$.secondaryMuscleGroups.length()").value(1)), "$.id");

        api.get("/api/exercises?scope=MINE").andExpect(jsonPath("$.totalElements").value(1));

        api.put("/api/exercises/{id}", CUSTOM.replace("Remada Pendlay", "Remada Pendlay pausada"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Remada Pendlay pausada"));

        api.patch("/api/exercises/{id}/active", "{\"active\":false}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        api.get("/api/exercises?scope=MINE").andExpect(jsonPath("$.totalElements").value(0));
        api.get("/api/exercises?scope=MINE&includeInactive=true").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsDuplicatedNameAndInvalidMuscleGroup() throws Exception {
        api.post("/api/exercises", CUSTOM).andExpect(status().isCreated());

        api.post("/api/exercises", CUSTOM.replace("Remada Pendlay", "  remada   PENDLAY "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        // mesmo nome de um exercício global
        api.post("/api/exercises", CUSTOM.replace("Remada Pendlay", "Supino reto com barra"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        // mesmo nome com outro equipamento é outro exercício
        api.post("/api/exercises", CUSTOM.replace("BARBELL", "SMITH")).andExpect(status().isCreated());

        api.post("/api/exercises", CUSTOM.replace("Remada Pendlay", "Outro").replace("\"BACK\",", "\"NOPE\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.primaryMuscleGroup").exists());
        api.post("/api/exercises", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void globalExercisesCannotBeEdited() throws Exception {
        String globalId = ApiClient.read(api.get("/api/exercises?search=cadeira extensora"), "$.content[0].id");

        api.get("/api/exercises/{id}", globalId).andExpect(status().isOk());
        api.put("/api/exercises/{id}", CUSTOM, globalId).andExpect(status().isNotFound());
        api.patch("/api/exercises/{id}/active", "{\"active\":false}", globalId).andExpect(status().isNotFound());
    }

    @Test
    void customExercisesAreInvisibleToOtherUsers() throws Exception {
        String id = ApiClient.read(api.post("/api/exercises", CUSTOM), "$.id");
        ApiClient other = new ApiClient(mvc, register(uniqueEmail()));

        other.get("/api/exercises/{id}", id).andExpect(status().isNotFound());
        other.put("/api/exercises/{id}", CUSTOM, id).andExpect(status().isNotFound());
        other.patch("/api/exercises/{id}/active", "{\"active\":false}", id).andExpect(status().isNotFound());
        List<String> names = ApiClient.read(other.get("/api/exercises?search=pendlay"), "$.content[*].name");
        assertThat(names).isEmpty();
        // o outro usuário pode criar um exercício com o mesmo nome
        other.post("/api/exercises", CUSTOM).andExpect(status().isCreated());
    }

    @Test
    void invalidQueryParametersAreRejected() throws Exception {
        api.get("/api/exercises?size=500").andExpect(status().isBadRequest());
        api.get("/api/exercises?equipment=SPACESHIP").andExpect(status().isBadRequest());
    }
}
