package com.gymwork.api.common.exception;

/** Login com e-mail ou senha inválidos (401). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos");
    }
}
