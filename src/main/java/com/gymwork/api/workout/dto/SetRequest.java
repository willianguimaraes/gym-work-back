package com.gymwork.api.workout.dto;

import com.gymwork.api.workout.SetStatus;
import com.gymwork.api.workout.SetType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registro ou correção de uma série. Carga em kg. Série concluída exige as repetições.
 *
 * @param clientId gerado no aparelho; reenviar a mesma série (fila offline) devolve a já gravada
 * @param parentSetId série principal de um drop set ou rest-pause (opcional)
 */
public record SetRequest(
        UUID clientId,
        @NotNull SetType setType,
        @NotNull SetStatus status,
        @DecimalMin("0") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal loadKg,
        @Min(0) @Max(1000) Integer reps,
        @Min(0) @Max(10) Integer rir,
        @Min(0) @Max(3600) Integer restSeconds,
        @Size(max = 1000) String notes,
        UUID parentSetId) {
}
