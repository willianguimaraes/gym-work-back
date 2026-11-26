package com.gymwork.api.storage;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mantém arquivos e banco coerentes: arquivos de registros apagados só somem depois do commit; um
 * arquivo gravado numa transação que falhou é removido no rollback.
 */
@Component
public class FileCleanup {

    private final StorageService storageService;

    public FileCleanup(StorageService storageService) {
        this.storageService = storageService;
    }

    /** Apaga os arquivos quando (e se) a transação atual for confirmada. */
    public void deleteAfterCommit(Collection<String> keys) {
        List<String> copy = List.copyOf(keys);
        if (copy.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            copy.forEach(storageService::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                copy.forEach(storageService::delete);
            }
        });
    }

    /** Remove o arquivo recém-gravado se a transação atual não for confirmada. */
    public void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageService.delete(key);
                }
            }
        });
    }
}
