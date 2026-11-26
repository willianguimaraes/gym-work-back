package com.gymwork.api.workout;

import java.math.BigDecimal;

/** Configuração planejada copiada para a sessão. Todos os campos podem ser nulos (exercício extra). */
record PlannedSnapshot(
        Integer sets, Integer repsMin, Integer repsMax, Integer restSeconds, Integer targetRir, BigDecimal loadKg) {

    static final PlannedSnapshot NONE = new PlannedSnapshot(null, null, null, null, null, null);
}
