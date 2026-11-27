package com.gymwork.api.auth;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.auth.dto.AuthResponse;
import com.gymwork.api.common.exception.InvalidCredentialsException;
import com.gymwork.api.common.exception.InvalidRefreshTokenException;
import com.gymwork.api.security.RefreshTokenCookie;
import com.gymwork.api.security.RefreshTokenService.IssuedRefreshToken;
import com.gymwork.api.support.WebSliceTestConfig;
import com.gymwork.api.user.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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

@WebMvcTest(AuthController.class)
@Import(WebSliceTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private AuthRateLimiter rateLimiter;

    @Test
    void registerReturns201WithAccessTokenInBodyAndRefreshTokenOnlyInHttpOnlyCookie() throws Exception {
        when(authService.register(any(), any())).thenReturn(result("novo-refresh"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@gym.com","password":"senha1234"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(RefreshTokenCookie.NAME + "=novo-refresh"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"))));
    }

    @Test
    void registerReturns400WithFieldErrorsWhenPayloadIsInvalid() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"invalido","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void loginReturns401WhenCredentialsAreInvalid() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@gym.com","password":"errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
    }

    @Test
    void refreshWithoutCookieReturns401AndClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshWithInvalidCookieReturns401AndClearsCookie() throws Exception {
        when(authService.refresh(eq("roubado"), any())).thenThrow(new InvalidRefreshTokenException());

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, "roubado")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void refreshWithValidCookieRotatesIt() throws Exception {
        when(authService.refresh(eq("antigo"), any())).thenReturn(result("novo"));

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, "antigo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(RefreshTokenCookie.NAME + "=novo")));
    }

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, "atual")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(authService).logout("atual");
    }

    @Test
    void forgotPasswordAlwaysReturns204() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"qualquer@gym.com"}
                                """))
                .andExpect(status().isNoContent());
        verify(passwordResetService).requestReset("qualquer@gym.com");
    }

    private static AuthResult result(String refreshValue) {
        AuthResponse body = new AuthResponse(
                "jwt", "Bearer", 900, new UserResponse(UUID.randomUUID(), "Ana", "ana@gym.com", Instant.now()));
        return new AuthResult(body, new IssuedRefreshToken(refreshValue, Instant.now().plus(Duration.ofDays(30))));
    }
}
