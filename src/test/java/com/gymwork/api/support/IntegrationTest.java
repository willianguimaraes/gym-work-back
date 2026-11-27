package com.gymwork.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymwork.api.mail.MailService;
import com.gymwork.api.security.RefreshTokenCookie;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integração: aplicação completa + PostgreSQL real em container (via URL
 * jdbc:tc, compartilhado entre as classes). Sem Docker, os testes são pulados.
 *
 * <p>A janela de tolerância de reuso do refresh token é zerada para testar a detecção de roubo
 * de forma determinística (o comportamento com tolerância é coberto nos testes unitários).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///gymwork",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "app.auth.refresh-reuse-grace=PT0S",
    "app.storage.local-dir=${java.io.tmpdir}/gymwork-test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "senha-forte-123";

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected MailService mailService;

    /** Sessão obtida no login/cadastro: token de acesso + valor do cookie de refresh. */
    public record Session(String accessToken, String refreshToken) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie cookie() {
            return new Cookie(RefreshTokenCookie.NAME, refreshToken);
        }
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@gym.test";
    }

    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    /** IPs 10.0.x.y distintos para os cadastros de teste. */
    protected static String nextIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.0." + (n / 250 % 250) + "." + (n % 250 + 1);
    }

    protected Session register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        // cada cadastro vem de um IP diferente: o limite por IP (AuthRateLimiter) vale como em produção
                        .with(request -> {
                            request.setRemoteAddr(nextIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Atleta","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    protected static Session session(MvcResult result) throws Exception {
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookie.NAME);
        return new Session(accessToken, cookie == null ? null : cookie.getValue());
    }
}
