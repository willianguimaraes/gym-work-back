package com.gymwork.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.IntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthFlowIntegrationTest extends IntegrationTest {

    @Test
    void registerThenAccessProtectedEndpointWithAccessToken() throws Exception {
        String email = uniqueEmail();
        Session session = register(email);

        assertThat(session.refreshToken()).isNotBlank();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicatedEmailIsRejectedIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro","email":"%s","password":"senha-forte-123"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void refreshRotatesTheCookieAndReuseOfTheOldOneRevokesTheSession() throws Exception {
        Session first = register(uniqueEmail());

        Session second = session(mvc.perform(post("/api/auth/refresh").cookie(first.cookie()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // Token antigo reapresentado = possível roubo: a sessão inteira é revogada
        mvc.perform(post("/api/auth/refresh").cookie(first.cookie()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second.cookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsOnlyTheCurrentSession() throws Exception {
        String email = uniqueEmail();
        Session phone = register(email);
        Session laptop = login(email, PASSWORD);

        mvc.perform(post("/api/auth/logout").cookie(phone.cookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(post("/api/auth/refresh").cookie(phone.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(laptop.cookie())).andExpect(status().isOk());
    }

    @Test
    void passwordResetFlowChangesPasswordAndEndsAllSessions() throws Exception {
        String email = uniqueEmail();
        Session before = register(email);

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(2000)).send(eq(email), anyString(), body.capture());
        Matcher link = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(link.find()).isTrue();
        String token = link.group(1);

        String resetBody = "{\"token\":\"%s\",\"newPassword\":\"nova-senha-456\"}".formatted(token);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isNoContent());

        // link de uso único
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").exists());
        // senha antiga não funciona mais, a nova sim
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(email, "nova-senha-456");
        // sessões anteriores foram encerradas
        mvc.perform(post("/api/auth/refresh").cookie(before.cookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordForUnknownEmailReturnsSameResponseAndSendsNothing() throws Exception {
        String unknown = uniqueEmail();

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(unknown)))
                .andExpect(status().isNoContent());

        verify(mailService, after(300).never()).send(eq(unknown), anyString(), anyString());
    }
}
