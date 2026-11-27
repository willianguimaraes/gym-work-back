package com.gymwork.api.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Importação de um planejamento no template {@value PlanTemplate#FORMAT}: usado tanto na
 * pré-visualização quanto na confirmação.
 *
 * @param content texto do arquivo ou colado do chat da IA (cercas ```json e texto em volta são ignorados)
 * @param planId opcional: adiciona as fases no fim deste planejamento em vez de criar um novo
 * @param exercises escolhas do usuário: nome do exercício como está no arquivo → exercício do catálogo
 */
public record PlanImportRequest(
        @NotBlank(message = "Cole o JSON ou escolha um arquivo") @Size(max = 200_000, message = "O texto pode ter no máximo 200 mil caracteres") String content,
        UUID planId,
        Map<String, UUID> exercises) {
}
