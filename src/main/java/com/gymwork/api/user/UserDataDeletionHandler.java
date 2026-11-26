package com.gymwork.api.user;

import java.util.UUID;

/**
 * Cada módulo que guarda dados do usuário (planos, treinos, avaliações, fotos…) implementa esta
 * interface para apagar seus dados na exclusão de conta. Use {@link org.springframework.core.annotation.Order}
 * para controlar a ordem (dependentes primeiro). Executado na mesma transação da exclusão.
 */
public interface UserDataDeletionHandler {

    void deleteUserData(UUID userId);
}
