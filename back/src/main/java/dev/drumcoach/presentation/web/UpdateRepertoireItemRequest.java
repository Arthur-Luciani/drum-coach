package dev.drumcoach.presentation.web;

import java.util.List;

import dev.drumcoach.domain.RepertoireItemStatus;

/**
 * Corpo de {@code PATCH /api/repertoire-items/{id}}. Campos {@code null} mantem o valor
 * atual; {@code newLinks} e sempre adicionado aos links existentes.
 */
public record UpdateRepertoireItemRequest(RepertoireItemStatus status, Integer currentBpm, String notes,
		List<RepertoireLinkRequest> newLinks) {
}
