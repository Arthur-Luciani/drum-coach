package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.RepertoireItem;
import dev.drumcoach.domain.RepertoireLink;

/**
 * Caso de uso: atualizar parcialmente um item de repertorio (status/BPM atual/notas)
 * e/ou adicionar novos links. A origem da escrita (USER|CLAUDE) e resolvida via {@link
 * OriginProvider} - nunca recebida do chamador (controller REST ou tool MCP).
 */
@Component
public class UpdateRepertoireItemUseCase {

	private final RepertoireItemRepository repertoireItemRepository;
	private final OriginProvider originProvider;

	public UpdateRepertoireItemUseCase(RepertoireItemRepository repertoireItemRepository,
			OriginProvider originProvider) {
		this.repertoireItemRepository = repertoireItemRepository;
		this.originProvider = originProvider;
	}

	public RepertoireItem execute(UpdateRepertoireItemCommand command) {
		RepertoireItem current = repertoireItemRepository.findById(command.id())
			.orElseThrow(() -> new NoSuchElementException("RepertoireItem nao encontrado: " + command.id()));
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		var newLinks = command.newLinks()
			.stream()
			.map(link -> RepertoireLink.createNew(link.url(), link.label(), now))
			.toList();
		RepertoireItem updated = current.withUpdate(command.status(), command.currentBpm(), command.notes(),
				newLinks, origin, now);
		return repertoireItemRepository.save(updated);
	}
}
