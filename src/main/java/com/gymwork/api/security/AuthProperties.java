package com.gymwork.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param refreshTokenTtl validade de cada refresh token (renovada a cada rotação)
 * @param refreshReuseGrace janela em que o reuso de um token recém-trocado não é tratado como roubo
 *     (duas abas renovando ao mesmo tempo)
 * @param cookieSecure envia o cookie apenas por HTTPS
 * @param passwordResetTtl validade do link de redefinição de senha
 * @param frontendUrl URL base do frontend, usada nos links enviados por e-mail
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration refreshReuseGrace,
        boolean cookieSecure,
        @NotNull Duration passwordResetTtl,
        @NotBlank String frontendUrl) {
}
