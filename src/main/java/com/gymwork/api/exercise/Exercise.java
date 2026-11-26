package com.gymwork.api.exercise;

import com.gymwork.api.common.SearchText;
import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Exercício do catálogo. {@code ownerId} nulo = global (seed, ninguém edita); com dono =
 * personalizado, visível e editável só pelo dono. Exercícios não são apagados, só desativados,
 * para não quebrar planos e históricos que os usam.
 */
@Entity
@Table(name = "exercises")
public class Exercise extends BaseEntity {

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "search_name", nullable = false, length = 120)
    private String searchName;

    @Column(name = "primary_muscle_group", nullable = false, length = 30)
    private String primaryMuscleGroup;

    @ElementCollection
    @CollectionTable(name = "exercise_secondary_muscles", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle_group", length = 30)
    private Set<String> secondaryMuscleGroups = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExerciseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Equipment equipment;

    @Column(length = 1000)
    private String description;

    @Column(length = 4000)
    private String instructions;

    @Column(nullable = false)
    private boolean active = true;

    protected Exercise() {
    }

    /** Cria um exercício personalizado do usuário. */
    public static Exercise custom(UUID ownerId) {
        Exercise exercise = new Exercise();
        exercise.ownerId = ownerId;
        return exercise;
    }

    public void update(
            String name,
            String primaryMuscleGroup,
            Set<String> secondaryMuscleGroups,
            ExerciseType type,
            Equipment equipment,
            String description,
            String instructions) {
        this.name = name;
        this.searchName = SearchText.normalize(name);
        this.primaryMuscleGroup = primaryMuscleGroup;
        this.secondaryMuscleGroups.clear();
        this.secondaryMuscleGroups.addAll(secondaryMuscleGroups);
        this.type = type;
        this.equipment = equipment;
        this.description = description;
        this.instructions = instructions;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isGlobal() {
        return ownerId == null;
    }

    public boolean isVisibleTo(UUID userId) {
        return ownerId == null || ownerId.equals(userId);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryMuscleGroup() {
        return primaryMuscleGroup;
    }

    public Set<String> getSecondaryMuscleGroups() {
        return secondaryMuscleGroups;
    }

    public ExerciseType getType() {
        return type;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public String getDescription() {
        return description;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isActive() {
        return active;
    }
}
