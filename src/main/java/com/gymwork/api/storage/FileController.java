package com.gymwork.api.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega arquivos privados por URL assinada e temporária (sem login: a assinatura é a
 * autorização). Token inválido, vencido ou arquivo apagado: 404, sem dizer qual.
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Arquivos")
public class FileController {

    private final SignedUrlService signedUrlService;
    private final StorageService storageService;

    public FileController(SignedUrlService signedUrlService, StorageService storageService) {
        this.signedUrlService = signedUrlService;
        this.storageService = storageService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Entrega o arquivo se a URL assinada for válida")
    public ResponseEntity<Resource> get(@PathVariable String token) {
        return signedUrlService.verify(token)
                .flatMap(key -> storageService.get(key).map(resource -> ResponseEntity.ok()
                        .contentType(mediaType(key))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(resource)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType mediaType(String key) {
        if (key.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (key.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
