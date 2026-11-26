package com.gymwork.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "app.jwt.secret precisa ter no mínimo 32 caracteres") String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer) {
}
