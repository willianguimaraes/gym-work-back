package com.gymwork.api.assessment;

import static com.gymwork.api.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.ApiClient;
import com.gymwork.api.support.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class AssessmentIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private JdbcTemplate jdbc;

    private Session session;
    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        session = register(uniqueEmail());
        api = new ApiClient(mvc, session);
    }

    private String assessment(LocalDate date, String extra) throws Exception {
        return read(api.post("/api/assessments", "{\"assessedOn\":\"%s\"%s}".formatted(date, extra))
                .andExpect(status().isCreated()), "$.id");
    }

    private ResultActions upload(Session as, String assessmentId, byte[] content, String name, String position)
            throws Exception {
        return mvc.perform(multipart("/api/assessments/{id}/photos", assessmentId)
                .file(new MockMultipartFile("file", name, "image/jpeg", content))
                .param("position", position)
                .header("Authorization", as.bearer()));
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void createsAssessmentWithOptionalValuesAndMeasurements() throws Exception {
        String id = read(api.post("/api/assessments", """
                        {"assessedOn":"%s","weightKg":82.4,"bodyFatPct":15.5,"notes":"Manhã, em jejum",
                         "measurements":[{"type":"WAIST","valueCm":84.5},{"type":"ARM_RIGHT","valueCm":38}]}
                        """.formatted(TODAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightKg").value(82.4))
                .andExpect(jsonPath("$.heightCm").doesNotExist())
                .andExpect(jsonPath("$.measurements.length()").value(2))
                .andExpect(jsonPath("$.photos.length()").value(0)), "$.id");

        // medidas enviadas substituem as atuais
        api.put("/api/assessments/{id}", """
                        {"assessedOn":"%s","weightKg":82.0,
                         "measurements":[{"type":"WAIST","valueCm":84},{"type":"CHEST","valueCm":102}]}
                        """.formatted(TODAY), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyFatPct").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.measurements.length()").value(2))
                .andExpect(jsonPath("$.measurements[?(@.type == 'WAIST')].valueCm").value(84.0))
                .andExpect(jsonPath("$.measurements[?(@.type == 'ARM_RIGHT')]").isEmpty());

        // registro rápido de peso
        assessment(TODAY, ",\"weightKg\":81.8");
        api.get("/api/assessments")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].measurementsCount".formatted(id)).value(2));
    }

    @Test
    void validatesAssessments() throws Exception {
        api.post("/api/assessments", "{\"assessedOn\":\"%s\"}".formatted(TODAY.plusDays(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assessedOn").exists());
        api.post("/api/assessments", "{\"assessedOn\":\"%s\",\"weightKg\":5,\"bodyFatPct\":90}".formatted(TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.weightKg").exists())
                .andExpect(jsonPath("$.errors.bodyFatPct").exists());
        api.post("/api/assessments", """
                        {"assessedOn":"%s","measurements":[{"type":"WAIST","valueCm":80},{"type":"WAIST","valueCm":81}]}
                        """.formatted(TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.measurements").exists());
        api.post("/api/assessments", "{\"weightKg\":80}").andExpect(status().isBadRequest());
    }

    @Test
    void photosAreValidatedStoredPrivatelyAndServedBySignedUrl() throws Exception {
        String id = assessment(TODAY, ",\"weightKg\":80");
        byte[] image = png(30, 40);

        String url = read(upload(session, id, image, "frente.png", "FRONT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value("FRONT"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.width").value(30))
                .andExpect(jsonPath("$.height").value(40))
                .andExpect(jsonPath("$.urlExpiresAt").exists()), "$.url");
        assertThat(url).startsWith("/api/files/");

        // a URL assinada funciona sem login e entrega o arquivo original
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(image));
        // token adulterado
        mvc.perform(get(url.substring(0, url.length() - 3) + "abc")).andExpect(status().isNotFound());

        // tipo é conferido pelos bytes, não pelo nome/Content-Type
        upload(session, id, "<html>oi</html>".getBytes(), "foto.jpg", "BACK")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").exists());
        upload(session, id, new byte[0], "vazio.jpg", "BACK").andExpect(status().isBadRequest());
        upload(session, id, new byte[(int) PhotoService.MAX_BYTES + 1], "grande.jpg", "BACK")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").exists());

        String photoId = read(api.get("/api/assessments/{id}", id), "$.photos[0].id");
        api.patch("/api/photos/{id}", "{\"position\":\"LEFT\"}", photoId)
                .andExpect(jsonPath("$.position").value("LEFT"));
        String fresh = read(api.get("/api/photos/{id}/url", photoId), "$.url");
        mvc.perform(get(fresh)).andExpect(status().isOk());

        api.delete("/api/photos/{id}", photoId).andExpect(status().isNoContent());
        mvc.perform(get(fresh)).andExpect(status().isNotFound());
        api.get("/api/assessments/{id}", id).andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void comparesTwoDatesWithoutInventingValues() throws Exception {
        String older = assessment(TODAY.minusDays(30), """
                ,"weightKg":84,"measurements":[{"type":"WAIST","valueCm":88},{"type":"ARM_RIGHT","valueCm":37}]""");
        String newer = assessment(TODAY, """
                ,"weightKg":82.5,"bodyFatPct":14,"measurements":[{"type":"WAIST","valueCm":85.5}]""");
        upload(session, older, png(10, 10), "a.png", "FRONT").andExpect(status().isCreated());
        upload(session, newer, png(10, 10), "b.png", "FRONT").andExpect(status().isCreated());
        upload(session, newer, png(10, 10), "c.png", "BACK").andExpect(status().isCreated());

        // ordem dos parâmetros não importa: "from" é sempre a mais antiga
        api.get("/api/assessments/compare?a={b}&b={a}", newer, older)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from.id").value(older))
                .andExpect(jsonPath("$.to.id").value(newer))
                .andExpect(jsonPath("$.daysBetween").value(30))
                .andExpect(jsonPath("$.items[?(@.metric == 'WEIGHT')].difference").value(-1.5))
                .andExpect(jsonPath("$.items[?(@.metric == 'WAIST')].difference").value(-2.5))
                // só um dos lados tem o valor: sem diferença calculada
                .andExpect(jsonPath("$.items[?(@.metric == 'BODY_FAT')].fromValue").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.items[?(@.metric == 'BODY_FAT')].difference").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.items[?(@.metric == 'ARM_RIGHT')].toValue").value(org.hamcrest.Matchers.contains((Object) null)))
                // medidas que nenhuma das duas tem não aparecem
                .andExpect(jsonPath("$.items[?(@.metric == 'CHEST')]").isEmpty())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].position").value("FRONT"))
                .andExpect(jsonPath("$.photos[0].from.url").exists())
                .andExpect(jsonPath("$.photos[1].position").value("BACK"))
                .andExpect(jsonPath("$.photos[1].from").doesNotExist());

        api.get("/api/assessments/compare?a={a}&b={b}", older, older).andExpect(status().isBadRequest());

        api.get("/api/assessments/trend")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].assessmentId").value(older))
                .andExpect(jsonPath("$[0].measurements.WAIST").value(88.0))
                .andExpect(jsonPath("$[1].weightKg").value(82.5));
    }

    @Test
    void otherUsersCannotSeeOrTouchAssessmentsAndPhotos() throws Exception {
        String id = assessment(TODAY, ",\"weightKg\":80");
        String other = assessment(TODAY, ",\"weightKg\":79");
        String photoId = read(upload(session, id, png(10, 10), "a.png", "FRONT"), "$.id");

        Session intruderSession = register(uniqueEmail());
        ApiClient intruder = new ApiClient(mvc, intruderSession);
        String own = read(intruder.post("/api/assessments", "{\"assessedOn\":\"%s\"}".formatted(TODAY)), "$.id");

        intruder.get("/api/assessments").andExpect(jsonPath("$.totalElements").value(1));
        intruder.get("/api/assessments/trend").andExpect(jsonPath("$.length()").value(1));
        intruder.get("/api/assessments/{id}", id).andExpect(status().isNotFound());
        intruder.put("/api/assessments/{id}", "{\"assessedOn\":\"%s\"}".formatted(TODAY), id)
                .andExpect(status().isNotFound());
        intruder.delete("/api/assessments/{id}", id).andExpect(status().isNotFound());
        intruder.get("/api/assessments/compare?a={a}&b={b}", id, other).andExpect(status().isNotFound());
        intruder.get("/api/assessments/compare?a={a}&b={b}", own, id).andExpect(status().isNotFound());
        upload(intruderSession, id, png(10, 10), "x.png", "FRONT").andExpect(status().isNotFound());
        intruder.get("/api/photos/{id}/url", photoId).andExpect(status().isNotFound());
        intruder.patch("/api/photos/{id}", "{\"position\":\"BACK\"}", photoId).andExpect(status().isNotFound());
        intruder.delete("/api/photos/{id}", photoId).andExpect(status().isNotFound());

        api.get("/api/assessments/{id}", id)
                .andExpect(jsonPath("$.weightKg").value(80.0))
                .andExpect(jsonPath("$.photos[0].position").value("FRONT"));
        // sem token, a API de fotos exige login
        mvc.perform(get("/api/photos/{id}/url", photoId)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAssessmentOrAccountRemovesTheFiles() throws Exception {
        String email = uniqueEmail();
        Session owner = register(email);
        ApiClient client = new ApiClient(mvc, owner);
        String id = read(client.post("/api/assessments", "{\"assessedOn\":\"%s\"}".formatted(TODAY)), "$.id");
        String keep = read(client.post("/api/assessments", "{\"assessedOn\":\"%s\"}".formatted(TODAY)), "$.id");
        String first = read(upload(owner, id, png(10, 10), "a.png", "FRONT"), "$.url");
        String second = read(upload(owner, keep, png(10, 10), "b.png", "FRONT"), "$.url");

        client.delete("/api/assessments/{id}", id).andExpect(status().isNoContent());
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(status().isOk());

        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        client.deleteJson("/api/me", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());
        mvc.perform(get(second)).andExpect(status().isNotFound());
        for (String table : java.util.List.of("body_assessments", "body_measurements", "progress_photos")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId))
                    .as(table).isZero();
        }
    }
}
