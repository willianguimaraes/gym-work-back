package com.gymwork.api.support;

import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Utilitários para testes unitários de entidades cujo ID é gerado pelo Hibernate. */
public final class TestEntities {

    private TestEntities() {
    }

    public static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static <T> T withId(T entity) {
        return withId(entity, UUID.randomUUID());
    }
}
