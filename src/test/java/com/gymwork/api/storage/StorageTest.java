package com.gymwork.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** URL assinada, storage local e detecção do tipo real das imagens. */
class StorageTest {

    private static final String SECRET = "test-secret-with-more-than-32-characters";

    @TempDir
    Path dir;

    private StorageProperties properties() {
        return new StorageProperties(dir.toString(), SECRET, Duration.ofMinutes(5));
    }

    @Test
    void signedUrlIsValidUntilItExpires() {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        SignedUrlService signer = new SignedUrlService(properties(), Clock.fixed(now, ZoneOffset.UTC));
        String token = signer.sign("photos/abc.jpg").url().substring("/api/files/".length());

        assertThat(signer.verify(token)).contains("photos/abc.jpg");

        SignedUrlService later = new SignedUrlService(properties(),
                Clock.fixed(now.plus(Duration.ofMinutes(5)).plusSeconds(1), ZoneOffset.UTC));
        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tamperedOrMalformedTokensAreRejected() {
        SignedUrlService signer = new SignedUrlService(properties(), Clock.systemUTC());
        String token = signer.sign("photos/abc.jpg").url().substring("/api/files/".length());
        String otherKey = signer.sign("photos/xyz.jpg").url().substring("/api/files/".length());

        // assinatura de outro arquivo com o payload deste
        String mixed = token.substring(0, token.indexOf('.')) + otherKey.substring(otherKey.indexOf('.'));
        assertThat(signer.verify(mixed)).isEmpty();
        assertThat(signer.verify("garbage")).isEmpty();
        assertThat(signer.verify("a.b")).isEmpty();
        assertThat(signer.verify(null)).isEmpty();

        SignedUrlService otherSecret = new SignedUrlService(
                new StorageProperties(dir.toString(), SECRET + "-other", Duration.ofMinutes(5)), Clock.systemUTC());
        assertThat(otherSecret.verify(token)).isEmpty();
    }

    @Test
    void secretMustBeLongEnough() {
        assertThatThrownBy(() -> new SignedUrlService(
                new StorageProperties(dir.toString(), "short", Duration.ofMinutes(5)), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void localStorageStoresReadsAndDeletes() {
        LocalStorageService storage = new LocalStorageService(properties());

        storage.put("photos/abc.jpg", new byte[] {1, 2, 3});
        assertThat(storage.get("photos/abc.jpg")).isPresent();

        storage.delete("photos/abc.jpg");
        storage.delete("photos/abc.jpg"); // não falha se já não existe
        assertThat(storage.get("photos/abc.jpg")).isEmpty();
    }

    @Test
    void localStorageRejectsUnsafeKeys() {
        LocalStorageService storage = new LocalStorageService(properties());

        assertThatThrownBy(() -> storage.put("../escape.jpg", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("photos/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("C:\\windows\\x.jpg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void imageTypeComesFromTheBytesNotTheName() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpg);

        assertThat(ImageInspector.inspect(png.toByteArray())).hasValueSatisfying(info -> {
            assertThat(info.type()).isEqualTo(ImageInspector.ImageType.PNG);
            assertThat(info.width()).isEqualTo(40);
            assertThat(info.height()).isEqualTo(30);
        });
        assertThat(ImageInspector.inspect(jpg.toByteArray()))
                .hasValueSatisfying(info -> assertThat(info.type()).isEqualTo(ImageInspector.ImageType.JPEG));

        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();
        assertThat(ImageInspector.inspect(webp))
                .hasValueSatisfying(info -> assertThat(info.type()).isEqualTo(ImageInspector.ImageType.WEBP));

        assertThat(ImageInspector.inspect("<html><script>alert(1)</script>".getBytes())).isEmpty();
        assertThat(ImageInspector.inspect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0}))
                .isEmpty(); // cabeçalho certo, imagem corrompida
    }
}
