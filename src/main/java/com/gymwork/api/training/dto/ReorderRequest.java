package com.gymwork.api.training.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Nova ordem: todos os IDs dos itens, cada um uma única vez. */
public record ReorderRequest(@NotEmpty List<UUID> ids) {
}
