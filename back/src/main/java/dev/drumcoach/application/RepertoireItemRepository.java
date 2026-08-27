package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.RepertoireItem;

/**
 * Port de persistencia de {@link RepertoireItem} (agregado com seus {@code
 * RepertoireLink}), implementado por {@code infra} (Spring Data JDBC). A camada
 * {@code application} so conhece esta interface - nunca depende diretamente de Spring
 * Data/JDBC.
 */
public interface RepertoireItemRepository {

	/**
	 * Persiste o item e seus links. Na criacao (id nulo), insere tudo. Na atualizacao,
	 * atualiza os campos do item e insere apenas os links ainda sem id (novos) - links ja
	 * existentes nunca sao reescritos/removidos.
	 */
	RepertoireItem save(RepertoireItem item);

	List<RepertoireItem> findAll();

	Optional<RepertoireItem> findById(long id);
}
