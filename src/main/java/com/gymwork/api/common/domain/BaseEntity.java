package com.gymwork.api.common.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entidade com identificador UUID v7 (ordenado no tempo, bom para índices e não adivinhável
 * como um sequencial).
 */
@MappedSuperclass
public abstract class BaseEntity extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id != null && Objects.equals(id, ((BaseEntity) other).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
