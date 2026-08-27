package dev.drumcoach.application;

import java.util.List;

import dev.drumcoach.domain.RepertoireItemStatus;

/**
 * Comando de entrada para {@link UpdateRepertoireItemUseCase} (PATCH parcial). Campos
 * {@code null} mantem o valor atual; {@code newLinks} e sempre adicionado aos links
 * existentes (nunca edita/remove um link ja persistido).
 */
public record UpdateRepertoireItemCommand(long id, RepertoireItemStatus status, Integer currentBpm, String notes,
		List<RepertoireLinkCommand> newLinks) {

	public UpdateRepertoireItemCommand {
		newLinks = newLinks == null ? List.of() : List.copyOf(newLinks);
	}
}
