package dev.drumcoach.application;

import java.util.List;

/** Comando de entrada para {@link CreateRepertoireItemUseCase}. */
public record CreateRepertoireItemCommand(String songTitle, String artist, Integer targetBpm, Integer currentBpm,
		String notes, List<RepertoireLinkCommand> links) {

	public CreateRepertoireItemCommand {
		links = links == null ? List.of() : List.copyOf(links);
	}
}
