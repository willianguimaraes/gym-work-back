package com.gymwork.api.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Arquivos em disco, numa pasta privada configurável ({@code app.storage.local-dir}). */
@Component
class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    /** Chaves são geradas pela aplicação; mesmo assim, só caracteres seguros (sem "..", sem "\"). */
    private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*\\.[a-z0-9]+");

    private final Path root;

    LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta de arquivos " + root, e);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        }
    }

    @Override
    public Optional<Resource> get(String key) {
        Path file = resolve(key);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo {}: {}", key, e.getMessage());
        }
    }

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chave de arquivo inválida");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de arquivo inválida");
        }
        return path;
    }
}
