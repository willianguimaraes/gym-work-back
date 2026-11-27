package com.gymwork.api.common.exception;

import java.time.Duration;

/** Tentativas demais num endpoint público (login, recuperação de senha, cadastro): 429. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Muitas tentativas. Tente de novo em " + minutes(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String minutes(Duration duration) {
        long minutes = Math.max(1, (duration.getSeconds() + 59) / 60);
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
