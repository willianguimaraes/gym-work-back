package com.gymwork.api.workout;

public enum ObservationCategory {
    PAIN,
    DISCOMFORT,
    RECOVERY,
    SLEEP,
    ENERGY,
    EXECUTION_DIFFICULTY,
    EQUIPMENT_UNAVAILABLE,
    GENERAL;

    /** Dor e desconforto aceitam região, lado e intensidade. */
    public boolean isBodyRelated() {
        return this == PAIN || this == DISCOMFORT;
    }
}
