package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.RepertoireItemStatus;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.RepertoireItem} (raiz do
 * agregado - sem os links, que sao mapeados separadamente por
 * {@link RepertoireLinkEntity}), mapeado via Spring Data JDBC para a tabela {@code
 * repertoire_item}. Nunca e exposto fora de {@code infra} - o domain.RepertoireItem e
 * mapeado de/para esta classe por {@link RepertoireItemMapper}.
 */
@Table("repertoire_item")
public record RepertoireItemEntity(@Id Long id, String songTitle, String artist, RepertoireItemStatus status,
		Integer targetBpm, Integer currentBpm, String notes, Origin createdBy, Origin lastModifiedBy,
		Instant createdAt, Instant updatedAt) {
}
