package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.RepertoireLink}, mapeado via
 * Spring Data JDBC para a tabela {@code repertoire_link}. Tabela filha sem auditoria
 * propria (ver ADR-0005). Nunca e exposto fora de {@code infra} - o
 * domain.RepertoireLink e mapeado de/para esta classe por {@link RepertoireItemMapper}.
 */
@Table("repertoire_link")
public record RepertoireLinkEntity(@Id Long id, Long repertoireItemId, String url, String label,
		Instant createdAt) {
}
