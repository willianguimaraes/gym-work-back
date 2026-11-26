package com.gymwork.api.user;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.support.WebSliceTestConfig;
import com.gymwork.api.user.dto.ProfileResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(WebSliceTestConfig.class)
class MeControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void usesTheUserIdFromTheTokenSubject() throws Exception {
        when(accountService.getProfile(USER_ID))
                .thenReturn(new ProfileResponse(USER_ID, "Ana", "ana@gym.com", null, null, Instant.now()));

        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana@gym.com"));
    }

    @Test
    void settingsAreValidated() throws Exception {
        mvc.perform(put("/api/me/settings")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weightUnit":"KG","lengthUnit":"CM","theme":"DARK",
                                 "defaultRestSeconds":5000,"restSoundEnabled":true,"timezone":"America/Sao_Paulo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultRestSeconds").exists());
        verifyNoInteractions(accountService);
    }

    @Test
    void deleteAccountClearsRefreshCookie() throws Exception {
        mvc.perform(delete("/api/me")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"senha"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(accountService).deleteAccount(USER_ID, "senha");
    }

    @Test
    void profileUpdateRejectsImplausibleHeight() throws Exception {
        mvc.perform(put("/api/me")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","heightCm":17.5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.heightCm").exists());
        verify(accountService, org.mockito.Mockito.never()).updateProfile(any(), any());
    }
}
