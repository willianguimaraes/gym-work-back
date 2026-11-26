package com.gymwork.api.storage;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * URLs temporárias para arquivos privados: {@code /api/files/{token}}, onde o token leva a chave do
 * arquivo e o vencimento, assinados com HMAC-SHA256. Funciona em {@code <img src>} (que não envia o
 * header Authorization) e segue o mesmo modelo das URLs pré-assinadas do S3.
 */
@Component
public class SignedUrlService {

    public record SignedUrl(String url, Instant expiresAt) {
    }

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final StorageProperties properties;
    private final Clock clock;
    private final byte[] secret;

    public SignedUrlService(StorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (properties.urlSecret() == null || properties.urlSecret().length() < 32) {
            throw new IllegalStateException("app.storage.url-secret precisa ter ao menos 32 caracteres");
        }
        this.secret = properties.urlSecret().getBytes(StandardCharsets.UTF_8);
    }

    public SignedUrl sign(String key) {
        Instant expiresAt = clock.instant().plus(properties.urlTtl());
        String payload = key + "|" + expiresAt.getEpochSecond();
        String token = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature(payload);
        return new SignedUrl("/api/files/" + token, expiresAt);
    }

    /** Chave do arquivo, se o token for autêntico e não estiver vencido. */
    public Optional<String> verify(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        try {
            String payload = new String(DECODER.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] expected = signature(payload).getBytes(StandardCharsets.UTF_8);
            byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, given)) {
                return Optional.empty();
            }
            int bar = payload.lastIndexOf('|');
            long expires = Long.parseLong(payload.substring(bar + 1));
            if (clock.instant().getEpochSecond() > expires) {
                return Optional.empty();
            }
            return Optional.of(payload.substring(0, bar));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
