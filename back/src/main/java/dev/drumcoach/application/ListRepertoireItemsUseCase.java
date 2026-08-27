package dev.drumcoach.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.RepertoireItem;

/** Caso de uso: listar todos os itens de repertorio cadastrados. */
@Component
public class ListRepertoireItemsUseCase {

	private final RepertoireItemRepository repertoireItemRepository;

	public ListRepertoireItemsUseCase(RepertoireItemRepository repertoireItemRepository) {
		this.repertoireItemRepository = repertoireItemRepository;
	}

	public List<RepertoireItem> execute() {
		return repertoireItemRepository.findAll();
	}
}
