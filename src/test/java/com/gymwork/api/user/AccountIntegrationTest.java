package com.gymwork.api.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AccountIntegrationTest extends IntegrationTest {

    @Test
    void newAccountStartsWithDefaultSettingsThatCanBeChanged() throws Exception {
        Session session = register(uniqueEmail());

        mvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightUnit").value("KG"))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.defaultRestSeconds").value(90))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));

        mvc.perform(put("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weightUnit":"LB","lengthUnit":"IN","theme":"LIGHT",
                                 "defaultRestSeconds":120,"restSoundEnabled":false,"timezone":"Europe/Lisbon"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.weightUnit").value("LB"))
                .andExpect(jsonPath("$.theme").value("LIGHT"))
                .andExpect(jsonPath("$.restSoundEnabled").value(false));
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        Session session = register(uniqueEmail());

        mvc.perform(put("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Souza","heightCm":172.5,"birthDate":"1990-05-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.heightCm").value(172.5))
                .andExpect(jsonPath("$.birthDate").value("1990-05-10"));
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndEndsOtherSessions() throws Exception {
        String email = uniqueEmail();
        Session phone = register(email);
        Session laptop = login(email, PASSWORD);

        mvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, phone.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"errada\",\"newPassword\":\"nova-senha-789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());

        Session renewed = session(mvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, phone.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"nova-senha-789\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn());

        mvc.perform(post("/api/auth/refresh").cookie(laptop.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(renewed.cookie())).andExpect(status().isOk());
        login(email, "nova-senha-789");
    }

    @Test
    void deleteAccountRequiresPasswordAndRemovesEverything() throws Exception {
        String email = uniqueEmail();
        Session session = register(email);

        mvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"errada\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        mvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        // o token de acesso ainda não expirou, mas o usuário não existe mais
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(session.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        // o e-mail fica livre para um novo cadastro
        register(email);
    }

    @Test
    void eachUserOnlyEverSeesTheirOwnAccount() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        Session userA = register(emailA);
        Session userB = register(emailB);

        mvc.perform(put("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, userA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weightUnit":"LB","lengthUnit":"IN","theme":"LIGHT",
                                 "defaultRestSeconds":60,"restSoundEnabled":false,"timezone":"UTC"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, userB.bearer()))
                .andExpect(jsonPath("$.email").value(emailB));
        mvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, userB.bearer()))
                .andExpect(jsonPath("$.weightUnit").value("KG"))
                .andExpect(jsonPath("$.theme").value("DARK"));
    }
}
