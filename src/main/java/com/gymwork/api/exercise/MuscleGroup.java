package com.gymwork.api.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Grupo muscular (tabela de referência, preenchida pela migration de seed). */
@Entity
@Table(name = "muscle_groups")
public class MuscleGroup {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private short position;

    protected MuscleGroup() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getPosition() {
        return position;
    }
}
