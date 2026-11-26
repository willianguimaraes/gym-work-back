package com.gymwork.api.workout;

import java.util.UUID;

/** Já existe uma sessão em andamento (409, com o ID dela para o app oferecer "retomar"). */
public class SessionInProgressException extends RuntimeException {

    private final UUID sessionId;

    public SessionInProgressException(UUID sessionId) {
        super("Já existe um treino em andamento");
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
