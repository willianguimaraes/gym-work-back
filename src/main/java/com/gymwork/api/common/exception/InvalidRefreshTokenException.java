package com.gymwork.api.common.exception;

/** Refresh token ausente, desconhecido, expirado ou revogado (401). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Sessão expirada. Faça login novamente.");
    }
}
