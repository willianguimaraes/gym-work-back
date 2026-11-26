package com.gymwork.api.workout;

/**
 * Tipo da série. Aquecimento e ativação não contam em volume nem progressão; drop set e rest-pause
 * contam no volume como parte da série principal (A13), não como séries extras.
 */
public enum SetType {
    WARMUP,
    ACTIVATION,
    WORKING,
    DROP_SET,
    REST_PAUSE,
    EXTRA;

    /** Conta como série realizada no progresso do exercício (trabalho ou adicional). */
    public boolean countsAsSet() {
        return this == WORKING || this == EXTRA;
    }

    /** Aquecimento e ativação ficam fora do volume e da progressão. */
    public boolean isPreparation() {
        return this == WARMUP || this == ACTIVATION;
    }
}
