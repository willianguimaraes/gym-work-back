package com.gymwork.api.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * Armazenamento de arquivos privados (fotos). A implementação atual grava em disco; uma futura
 * implementação S3 só precisa cumprir este contrato. Nada é servido por URL pública: o acesso é
 * por {@link SignedUrlService}.
 */
public interface StorageService {

    void put(String key, byte[] content);

    Optional<Resource> get(String key);

    /** Remove o arquivo; não falha se ele já não existir. */
    void delete(String key);
}
