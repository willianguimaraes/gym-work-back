package com.gymwork.api.report;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ReportIntegrationTest extends IntegrationTest {

    private ApiClient api;
    private String planId;
    private String phaseId;
    private String upperId;
    private String sessionId;
    private LocalDate today;

    /**
     * Fase começou há 7 dias: a semana 1 já passou sem treino; o treino de hoje (UPPER) cai na
     * semana 2, com supino, elevação lateral pulada e remada substituída.
     */
    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, register(uniqueEmail()));
        today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String bench = exercise("supino reto com barra");
        String lateral = exercise("elevação lateral com halteres");
        String row = exercise("remada baixa na polia");

        planId = read(api.post("/api/plans", "{\"name\":\"Hipertrofia\",\"goal\":\"HYPERTROPHY\"}"), "$.id");
        phaseId = read(api.post("/api/plans/{id}/phases", """
                {"name":"Fase 4 — Controle","startDate":"%s","weeksCount":4}
                """.formatted(today.minusDays(7)), planId), "$.id");
        upperId = read(api.post("/api/phases/{id}/days", "{\"name\":\"UPPER\"}", phaseId), "$.id");
        api.post("/api/phases/{id}/days", "{\"name\":\"LOWER\"}", phaseId).andExpect(status().isCreated());
        planned(upperId, bench, 3, 6, 8);
        planned(upperId, lateral, 3, 12, 15);
        planned(upperId, row, 3, 8, 10);

        api.post("/api/assessments", "{\"assessedOn\":\"%s\",\"weightKg\":100}".formatted(today))
                .andExpect(status().isCreated());

        sessionId = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(upperId)), "$.id");
        String benchItem = item("Supino reto com barra");
        set(benchItem, "WARMUP", "COMPLETED", "20", 12);
        set(benchItem, "WORKING", "COMPLETED", "60", 8);
        set(benchItem, "WORKING", "COMPLETED", "60", 8);
        set(benchItem, "WORKING", "COMPLETED", "60", 7);
        api.post("/api/session-exercises/{id}/skip", null, item("Elevação lateral com halteres")).andExpect(status().isOk());
        String articulated = exercise("remada articulada");
        api.post("/api/session-exercises/{id}/substitute", "{\"exerciseId\":\"%s\"}".formatted(articulated),
                item("Remada baixa na polia")).andExpect(status().isOk());
        String replacement = item("Remada articulada");
        set(replacement, "WORKING", "COMPLETED", "50", 10);
        set(replacement, "WORKING", "COMPLETED", "50", 10);
        api.post("/api/observations", """
                {"category":"PAIN","sessionExerciseId":"%s","bodyRegion":"SHOULDER","bodySide":"RIGHT","intensity":6}
                """.formatted(benchItem)).andExpect(status().isCreated());
        api.post("/api/sessions/{id}/finish", "{\"notes\":\"Treino concluído normalmente.\"}", sessionId)
                .andExpect(status().isOk());
    }

    @Test
    void sessionReportUsesTheTrackingFormat() throws Exception {
        String text = text(api.get("/api/reports/sessions/{id}", sessionId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().doesNotExist("Content-Disposition")));
        String weekday = ReportLabels.weekday(today.getDayOfWeek());

        assertThat(text).startsWith("""
                ══════════════════════
                FASE 4 — CONTROLE
                ══════════════════════

                SEMANA 2
                Peso: 100 kg

                %s — UPPER

                Supino reto com barra: 60 kg | 8/8/7
                Elevação lateral com halteres: não realizado
                Remada baixa na polia → Remada articulada: 50 kg | 10/10/x

                Observações:
                Treino concluído normalmente.
                [Dor] Ombro D, intensidade 6 (Supino reto com barra)
                ________________________________
                """.formatted(weekday));

        api.get("/api/reports/sessions/{id}?download=true", sessionId)
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("relatorio-fase-4-controle-" + today + ".txt")));
    }

    @Test
    void phaseAndWeekReportsListMissedDays() throws Exception {
        String weekOne = read(api.get("/api/phases/{id}", phaseId), "$.weeks[0].id");
        String weekTwo = read(api.get("/api/phases/{id}", phaseId), "$.weeks[1].id");

        String week = text(api.get("/api/reports/weeks/{id}", weekOne).andExpect(status().isOk()));
        assertThat(week).contains("SEMANA 1\n\nUPPER\n\nNão realizado\n" + ReportText.SEPARATOR)
                .contains("LOWER\n\nNão realizado")
                .doesNotContain("Peso:");

        String phase = text(api.get("/api/reports/phases/{id}", phaseId).andExpect(status().isOk()));
        assertThat(phase.indexOf("SEMANA 1")).isLessThan(phase.indexOf("SEMANA 2"));
        assertThat(phase).contains("Supino reto com barra: 60 kg | 8/8/7").doesNotContain("SEMANA 3");

        String current = text(api.get("/api/reports/weeks/{id}", weekTwo));
        assertThat(current).contains("Peso: 100 kg").contains("— UPPER");
    }

    @Test
    void csvAndPdfExports() throws Exception {
        String csv = text(api.get("/api/reports/sessions/{id}?format=csv", sessionId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".csv"))));
        List<String> rows = csv.lines().toList();
        assertThat(rows.get(0)).isEqualTo("﻿data;semana;dia;exercicio;substituiu;serie;tipo;carga_kg;reps;rir;status;observacao");
        assertThat(rows).anyMatch(r -> r.contains(";UPPER;Supino reto com barra;;;Aquecimento;20;12;"));
        assertThat(rows).anyMatch(r -> r.contains(";Supino reto com barra;;3;Trabalho;60;7;"));
        assertThat(rows).anyMatch(r -> r.contains(";Elevação lateral com halteres;;;;;;;Pulado;"));
        assertThat(rows).anyMatch(r -> r.contains(";Remada articulada;Remada baixa na polia;1;Trabalho;50;10;"));

        byte[] pdf = api.get("/api/reports/phases/{id}?format=pdf", phaseId)
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        api.get("/api/reports/sessions/{id}?format=docx", sessionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.format").exists());
    }

    @Test
    void analysisSummarizesThePeriod() throws Exception {
        // segunda sessão: supino evolui na mesma carga
        String second = read(api.post("/api/sessions", "{\"workoutDayId\":\"%s\"}".formatted(upperId)), "$.id");
        String benchItem = read(api.get("/api/sessions/{id}", second), "$.exercises[0].id");
        set(benchItem, "WORKING", "COMPLETED", "60", 8);
        set(benchItem, "WORKING", "COMPLETED", "60", 8);
        set(benchItem, "WORKING", "COMPLETED", "60", 8);
        api.post("/api/sessions/{id}/finish", null, second).andExpect(status().isOk());

        api.get("/api/reports/phases/{id}/analysis", phaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PHASE"))
                .andExpect(jsonPath("$.title").value("Fase 4 — Controle"))
                .andExpect(jsonPath("$.frequency.completedSessions").value(2))
                .andExpect(jsonPath("$.frequency.plannedSessions").value(4))
                .andExpect(jsonPath("$.frequency.missedDays").value(2))
                .andExpect(jsonPath("$.progressed[0].exerciseName").value("Supino reto com barra"))
                .andExpect(jsonPath("$.progressed[0].baselineSummary").value("60 kg — 8/8/7"))
                .andExpect(jsonPath("$.progressed[0].latestSummary").value("60 kg — 8/8/8"))
                .andExpect(jsonPath("$.repChanges[0]").value("Supino reto com barra (60 kg): 8/8/7 → 8/8/8"))
                .andExpect(jsonPath("$.notPerformed[?(@.exercise == 'Elevação lateral com halteres')].detail")
                        .value(Matchers.hasItem("pulado")))
                .andExpect(jsonPath("$.notPerformed[?(@.exercise == 'Remada articulada')].detail")
                        .value(Matchers.hasItem("2 de 3 séries")))
                .andExpect(jsonPath("$.discomforts[0]").value(Matchers.containsString("[Dor] Ombro D, intensidade 6")))
                .andExpect(jsonPath("$.observations[0]").value(Matchers.endsWith("Treino concluído normalmente.")))
                .andExpect(jsonPath("$.volume[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.volume[0].sets").value(6));

        api.get("/api/reports/sessions/{id}/analysis", second)
                .andExpect(jsonPath("$.frequency.completedSessions").value(1))
                .andExpect(jsonPath("$.frequency.plannedSessions").doesNotExist())
                .andExpect(jsonPath("$.progressed[0].baselineSummary").value("60 kg — 8/8/7"));
    }

    @Test
    void templateAndAiPackage() throws Exception {
        api.get("/api/plans/{id}/template?download=true", planId)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("plano-hipertrofia.json")))
                .andExpect(jsonPath("$.format").value("gymwork.plan/v1"))
                .andExpect(jsonPath("$.plan.goal").value("HYPERTROPHY"))
                .andExpect(jsonPath("$.phases[0].weeks").value(4))
                .andExpect(jsonPath("$.phases[0].deloadWeeks.length()").value(0))
                .andExpect(jsonPath("$.phases[0].days[0].name").value("UPPER"))
                .andExpect(jsonPath("$.phases[0].days[0].exercises[0].exercise").value("Supino reto com barra"))
                .andExpect(jsonPath("$.phases[0].days[0].exercises[0].repsMax").value(8))
                .andExpect(jsonPath("$.phases[0].days[0].exercises[0].suggestedLoadKg").value(Matchers.nullValue()));

        String md = text(api.get("/api/reports/phases/{id}/ai-package", phaseId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown")));
        assertThat(md).startsWith("# Pacote de acompanhamento — Fase 4 — Controle")
                .contains("## 1. Instruções", "## 3. Planejamento atual (template gymwork.plan/v1)",
                        "\"format\" : \"gymwork.plan/v1\"", "Supino reto com barra: 60 kg | 8/8/7",
                        "## 7. Avaliações físicas", "| Data | Peso (kg) |", "## 8. Exercícios disponíveis no catálogo",
                        "Remada cavalinho");

        api.get("/api/reports/phases/{id}/ai-package?format=json&download=true", phaseId)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("pacote-ia-fase-4-controle.json")))
                .andExpect(jsonPath("$.format").value("gymwork.ai-package/v1"))
                .andExpect(jsonPath("$.currentPlan.format").value("gymwork.plan/v1"))
                .andExpect(jsonPath("$.context.weightUnit").value("kg"))
                .andExpect(jsonPath("$.assessments[0].weightKg").value(100))
                .andExpect(jsonPath("$.analysis.discomforts.length()").value(1))
                .andExpect(jsonPath("$.catalog.length()").value(Matchers.greaterThan(50)));
    }

    @Test
    void otherUsersGetNotFound() throws Exception {
        ApiClient other = new ApiClient(mvc, register(uniqueEmail()));
        String weekId = read(api.get("/api/phases/{id}", phaseId), "$.weeks[0].id");
        for (String path : List.of(
                "/api/reports/sessions/" + sessionId,
                "/api/reports/sessions/" + sessionId + "?format=csv",
                "/api/reports/sessions/" + sessionId + "/analysis",
                "/api/reports/weeks/" + weekId,
                "/api/reports/weeks/" + weekId + "/analysis",
                "/api/reports/phases/" + phaseId + "?format=pdf",
                "/api/reports/phases/" + phaseId + "/analysis",
                "/api/reports/phases/" + phaseId + "/ai-package",
                "/api/plans/" + planId + "/template",
                "/api/phases/" + phaseId + "/template")) {
            other.get(path).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------ apoio

    private String exercise(String search) throws Exception {
        return read(api.get("/api/exercises?scope=GLOBAL&search=" + search), "$.content[0].id");
    }

    private void planned(String dayId, String exerciseId, int sets, int min, int max) throws Exception {
        api.post("/api/days/{id}/exercises", """
                {"exerciseId":"%s","sets":%d,"repsMin":%d,"repsMax":%d}
                """.formatted(exerciseId, sets, min, max), dayId).andExpect(status().isCreated());
    }

    private String item(String name) throws Exception {
        List<String> ids = read(api.get("/api/sessions/{id}", sessionId),
                "$.exercises[?(@.exerciseName == '" + name + "')].id");
        return ids.get(0);
    }

    private void set(String itemId, String type, String status, String load, int reps) throws Exception {
        api.post("/api/session-exercises/{id}/sets", """
                {"setType":"%s","status":"%s","loadKg":%s,"reps":%d}
                """.formatted(type, status, load, reps), itemId).andExpect(status().isOk());
    }

    private static String text(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
