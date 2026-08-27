package dev.drumcoach.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.RepertoireItem;
import dev.drumcoach.domain.RepertoireLink;

/**
 * Caso de uso: cadastrar um item de repertorio, com seus links (agregado persistido numa
 * unica operacao). A origem da escrita (USER|CLAUDE) e resolvida via {@link
 * OriginProvider} - nunca recebida do chamador (controller REST ou tool MCP). Os links
 * nao tem auditoria propria: a origem/timestamp sao os do item pai (ver ADR-0005).
 */
@Component
public class CreateRepertoireItemUseCase {

	private final RepertoireItemRepository repertoireItemRepository;
	private final OriginProvider originProvider;

	public CreateRepertoireItemUseCase(RepertoireItemRepository repertoireItemRepository,
			OriginProvider originProvider) {
		this.repertoireItemRepository = repertoireItemRepository;
		this.originProvider = originProvider;
	}

	public RepertoireItem execute(CreateRepertoireItemCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		var links = command.links()
			.stream()
			.map(link -> RepertoireLink.createNew(link.url(), link.label(), now))
			.toList();
		RepertoireItem item = RepertoireItem.createNew(command.songTitle(), command.artist(), command.targetBpm(),
				command.currentBpm(), command.notes(), links, origin, now);
		return repertoireItemRepository.save(item);
	}
}
