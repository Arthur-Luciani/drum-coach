package dev.drumcoach.infra.persistence;

import java.util.List;

import dev.drumcoach.domain.RepertoireItem;
import dev.drumcoach.domain.RepertoireLink;

/**
 * Mapeamento domain.RepertoireItem (+ seus RepertoireLink) <-> registros de persistencia,
 * so via construtor/factory.
 */
final class RepertoireItemMapper {

	private RepertoireItemMapper() {
	}

	static RepertoireItemEntity toEntity(RepertoireItem item) {
		return new RepertoireItemEntity(item.id(), item.songTitle(), item.artist(), item.status(), item.targetBpm(),
				item.currentBpm(), item.notes(), item.createdBy(), item.lastModifiedBy(), item.createdAt(),
				item.updatedAt());
	}

	static RepertoireLinkEntity toEntity(RepertoireLink link, long repertoireItemId) {
		return new RepertoireLinkEntity(link.id(), repertoireItemId, link.url(), link.label(), link.createdAt());
	}

	static RepertoireLink toDomain(RepertoireLinkEntity entity) {
		return RepertoireLink.reconstruct(entity.id(), entity.repertoireItemId(), entity.url(), entity.label(),
				entity.createdAt());
	}

	static RepertoireItem toDomain(RepertoireItemEntity entity, List<RepertoireLink> links) {
		return RepertoireItem.reconstruct(entity.id(), entity.songTitle(), entity.artist(), entity.status(),
				entity.targetBpm(), entity.currentBpm(), entity.notes(), links, entity.createdBy(),
				entity.lastModifiedBy(), entity.createdAt(), entity.updatedAt());
	}
}
