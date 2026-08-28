package dev.drumcoach.application;

import java.util.List;

import dev.drumcoach.domain.RepertoireItemStatus;

/** Comando de entrada para {@link CreateRepertoireItemUseCase}. {@code status} nulo = {@code NOT_STARTED}. */
public record CreateRepertoireItemCommand(String songTitle, String artist, RepertoireItemStatus status,
		Integer targetBpm, Integer currentBpm, String notes, List<RepertoireLinkCommand> links) {

	public CreateRepertoireItemCommand {
		links = links == null ? List.of() : List.copyOf(links);
	}
}
