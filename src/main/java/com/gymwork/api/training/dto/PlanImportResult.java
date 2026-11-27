package com.gymwork.api.training.dto;

import java.util.List;
import java.util.UUID;

/** Planejamento criado (ou que recebeu as fases) e as fases criadas, na ordem do arquivo. */
public record PlanImportResult(UUID planId, List<UUID> phaseIds) {
}
