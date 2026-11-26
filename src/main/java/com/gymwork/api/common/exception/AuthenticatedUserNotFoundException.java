package com.gymwork.api.common.exception;

/** Token válido de um usuário que não existe mais (conta excluída) — tratado como 401. */
public class AuthenticatedUserNotFoundException extends RuntimeException {

    public AuthenticatedUserNotFoundException() {
        super("Usuário não encontrado. Faça login novamente.");
    }
}
