package dev.drumcoach.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.application.RepertoireItemRepository;
import dev.drumcoach.domain.RepertoireItem;
import dev.drumcoach.domain.RepertoireLink;

/**
 * Implementacao do port {@link RepertoireItemRepository} via Spring Data JDBC. {@code
 * RepertoireItemEntity} e {@code RepertoireLinkEntity} sao tabelas flat separadas - esta
 * classe orquestra explicitamente a raiz + os links filhos numa unica transacao. Ao
 * salvar, links ja persistidos (com id) nunca sao reescritos; so os links novos (id nulo,
 * vindos de um PATCH que adiciona link) sao inseridos - preserva o historico dos links
 * existentes.
 */
@Repository
public class RepertoireItemRepositoryImpl implements RepertoireItemRepository {

	private final SpringDataRepertoireItemRepository itemJdbcRepository;
	private final SpringDataRepertoireLinkRepository linkJdbcRepository;

	public RepertoireItemRepositoryImpl(SpringDataRepertoireItemRepository itemJdbcRepository,
			SpringDataRepertoireLinkRepository linkJdbcRepository) {
		this.itemJdbcRepository = itemJdbcRepository;
		this.linkJdbcRepository = linkJdbcRepository;
	}

	@Override
	@Transactional
	public RepertoireItem save(RepertoireItem item) {
		RepertoireItemEntity savedRoot = itemJdbcRepository.save(RepertoireItemMapper.toEntity(item));
		for (RepertoireLink link : item.links()) {
			if (link.id() == null) {
				linkJdbcRepository.save(RepertoireItemMapper.toEntity(link, savedRoot.id()));
			}
		}
		return toDomainWithLinks(savedRoot);
	}

	@Override
	public List<RepertoireItem> findAll() {
		return StreamSupport.stream(itemJdbcRepository.findAll().spliterator(), false)
			.map(this::toDomainWithLinks)
			.toList();
	}

	@Override
	public Optional<RepertoireItem> findById(long id) {
		return itemJdbcRepository.findById(id).map(this::toDomainWithLinks);
	}

	private RepertoireItem toDomainWithLinks(RepertoireItemEntity entity) {
		List<RepertoireLink> links = linkJdbcRepository.findByRepertoireItemId(entity.id())
			.stream()
			.map(RepertoireItemMapper::toDomain)
			.toList();
		return RepertoireItemMapper.toDomain(entity, links);
	}
}
