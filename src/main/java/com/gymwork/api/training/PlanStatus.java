package com.gymwork.api.training;

/** Só um planejamento ACTIVE por usuário (índice parcial no banco). */
public enum PlanStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    ARCHIVED
}
