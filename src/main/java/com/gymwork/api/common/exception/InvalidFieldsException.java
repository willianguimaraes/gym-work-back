package com.gymwork.api.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vários campos inválidos de uma vez (400, com {@code errors: {campo: mensagem}}). */
public class InvalidFieldsException extends RuntimeException {

    private final transient Map<String, String> errors;

    public InvalidFieldsException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
