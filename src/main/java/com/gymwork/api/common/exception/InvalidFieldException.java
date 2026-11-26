package com.gymwork.api.common.exception;

/**
 * Regra de negócio violada por um campo específico (400), no mesmo formato dos erros de validação.
 * Ex.: senha atual incorreta, link de redefinição expirado.
 */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
