package com.gymwork.api.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param localDir pasta dos arquivos (fora da pasta servida pela aplicação)
 * @param urlSecret segredo HMAC das URLs assinadas (mínimo 32 caracteres em produção)
 * @param urlTtl validade das URLs assinadas
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String localDir, String urlSecret, Duration urlTtl) {
}
