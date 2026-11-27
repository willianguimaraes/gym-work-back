package com.gymwork.api.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymwork.api.training.dto.PlanImportPreview.Issue;
import com.gymwork.api.training.dto.PlanTemplate;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PlanTemplateReaderTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String VALID = """
            {
              "format": "gymwork.plan/v1",
              "plan": { "name": "Hipertrofia 2027", "goal": "HYPERTROPHY", "notes": null },
              "phases": [{
                "name": "Fase 5 — Volume", "weeks": 8, "deloadWeeks": [8], "notes": "Subir volume",
                "days": [{
                  "name": "UPPER", "dayOfWeek": "MONDAY", "notes": null,
                  "exercises": [{
                    "exercise": "Supino inclinado máquina",
                    "sets": 4, "repsMin": 6, "repsMax": 10,
                    "restSeconds": 120, "targetRir": 2, "suggestedLoadKg": 45, "notes": null
                  }]
                }]
              }]
            }
            """;

    @Test
    void readsTheTemplateExportedBySystem() {
        PlanTemplateReader.Result result = read(VALID);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        PlanTemplate template = result.template();
        assertThat(template.plan().name()).isEqualTo("Hipertrofia 2027");
        assertThat(template.plan().goal()).isEqualTo(PlanGoal.HYPERTROPHY);
        PlanTemplate.Phase phase = template.phases().get(0);
        assertThat(phase.weeks()).isEqualTo(8);
        assertThat(phase.deloadWeeks()).containsExactly(8);
        PlanTemplate.Exercise exercise = phase.days().get(0).exercises().get(0);
        assertThat(exercise.exercise()).isEqualTo("Supino inclinado máquina");
        assertThat(exercise.sets()).isEqualTo(4);
        assertThat(exercise.suggestedLoadKg()).isEqualByComparingTo("45");
    }

    @Test
    void acceptsTheWayChatAnswersUsuallyLook() {
        String answer = """
                Claro! Aqui está o próximo planejamento:

                ```json
                {
                  "plan": { "name": "  Força   2027 ", "goal": "strength" },
                  "phases": [{
                    "name": "Base", "weeks": "6", "deloadWeeks": [6, 6],
                    "days": [{
                      "name": "A", "dayOfWeek": "Quarta-feira",
                      "exercises": [{ "exercise": "Agachamento livre", "sets": 5.0, "repsMin": 3, "repsMax": 5,
                                      "suggestedLoadKg": "102,5", "tempo": "3-1-1" }]
                    }]
                  }]
                }
                ```

                Bons treinos!
                """;
        PlanTemplateReader.Result result = read(answer);

        assertThat(result.errors()).isEmpty();
        PlanTemplate template = result.template();
        assertThat(template.plan().name()).isEqualTo("Força 2027");
        assertThat(template.plan().goal()).isEqualTo(PlanGoal.STRENGTH);
        PlanTemplate.Phase phase = template.phases().get(0);
        assertThat(phase.weeks()).isEqualTo(6);
        assertThat(phase.deloadWeeks()).containsExactly(6);
        assertThat(phase.days().get(0).dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        PlanTemplate.Exercise exercise = phase.days().get(0).exercises().get(0);
        assertThat(exercise.sets()).isEqualTo(5);
        assertThat(exercise.suggestedLoadKg()).isEqualTo(new BigDecimal("102.5"));
        assertThat(result.warnings()).extracting(Issue::path).containsExactlyInAnyOrder(
                "format", "phases[0].deloadWeeks[1]", "phases[0].days[0].exercises[0].tempo");
    }

    @Test
    void listsEveryProblemWithItsPathAndLocation() {
        String json = """
                {
                  "format": "gymwork.plan/v1",
                  "plan": { "name": "", "goal": "BULK" },
                  "phases": [{
                    "name": "Fase 1", "weeks": 4, "deloadWeeks": [5],
                    "days": [{
                      "name": "UPPER", "dayOfWeek": "FUNDAY",
                      "exercises": [
                        { "exercise": "Supino", "sets": 0, "repsMin": 12, "repsMax": 8, "restSeconds": 1000 },
                        { "sets": 3, "repsMin": 8, "repsMax": 12, "targetRir": 11, "suggestedLoadKg": -1 }
                      ]
                    }]
                  }]
                }
                """;
        PlanTemplateReader.Result result = read(json);

        assertThat(result.template()).isNull();
        assertThat(result.errors()).extracting(Issue::path).containsExactly(
                "plan.name",
                "plan.goal",
                "phases[0].deloadWeeks[0]",
                "phases[0].days[0].dayOfWeek",
                "phases[0].days[0].exercises[0].sets",
                "phases[0].days[0].exercises[0].restSeconds",
                "phases[0].days[0].exercises[0].repsMax",
                "phases[0].days[0].exercises[1].exercise",
                "phases[0].days[0].exercises[1].targetRir",
                "phases[0].days[0].exercises[1].suggestedLoadKg");
        Issue reps = result.errors().get(6);
        assertThat(reps.location()).isEqualTo("Fase 1 (Fase 1) › Dia 1 (UPPER) › Exercício 1 (Supino)");
        assertThat(reps.message()).contains("repsMin");
    }

    @Test
    void reportsBrokenJsonWithTheLineOfTheOriginalText() {
        String text = "Segue o plano:\n\n{\n  \"plan\": { \"name\": \"X\" \"goal\": \"OTHER\" },\n  \"phases\": []\n}";
        List<Issue> errors = read(text).errors();

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).startsWith("JSON inválido perto da linha 4");
    }

    @Test
    void rejectsOtherFormatsAndMissingContent() {
        assertThat(read("só texto, sem JSON").errors()).extracting(Issue::message)
                .containsExactly("Não encontrei um JSON no texto. Ele deve começar com { e terminar com }.");
        assertThat(read("{\"format\":\"gymwork.ai-package/v1\",\"plan\":{\"name\":\"A\",\"goal\":\"OTHER\"},\"phases\":[]}")
                        .errors())
                .extracting(Issue::path)
                .containsExactly("format", "phases");
        assertThat(read("[1, 2]").errors()).hasSize(1);
    }

    @Test
    void planBlockIsOptionalWhenAddingPhasesToAnExistingPlan() {
        String json = """
                {"format":"gymwork.plan/v1","phases":[{"name":"Fase 6","weeks":4,"days":[]}]}
                """;
        assertThat(PlanTemplateReader.read(MAPPER, json, false).errors()).isEmpty();
        assertThat(PlanTemplateReader.read(MAPPER, json, true).errors()).extracting(Issue::path).containsExactly("plan");
    }

    @Test
    void enforcesTheSizeLimits() {
        String day = "{\"name\":\"D\",\"exercises\":[]}";
        String days = String.join(",", java.util.Collections.nCopies(PlanTemplateReader.MAX_DAYS + 1, day));
        String json = "{\"plan\":{\"name\":\"A\",\"goal\":\"OTHER\"},\"phases\":[{\"name\":\"F\",\"weeks\":53,\"days\":[" + days + "]}]}";

        assertThat(read(json).errors()).extracting(Issue::path).containsExactly("phases[0].weeks", "phases[0].days");
    }

    private static PlanTemplateReader.Result read(String content) {
        return PlanTemplateReader.read(MAPPER, content, true);
    }
}
