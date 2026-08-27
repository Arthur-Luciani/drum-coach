package dev.drumcoach.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Link de referencia (video, partitura, etc.) de um {@link RepertoireItem}. Linha filha
 * sem auditoria propria (created_by/last_modified_by) - a origem e a do item de
 * repertorio pai (ver ADR-0005).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.RepertoireLinkEntity}).
 */
public final class RepertoireLink {

	private final Long id;
	private final Long repertoireItemId;
	private final String url;
	private final String label;
	private final Instant createdAt;

	private RepertoireLink(Long id, Long repertoireItemId, String url, String label, Instant createdAt) {
		this.id = id;
		this.repertoireItemId = repertoireItemId;
		this.url = Objects.requireNonNull(url, "url must not be null");
		this.label = label;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	/** Cria um novo link ainda sem id/repertoireItemId (a persistir junto do item pai). */
	public static RepertoireLink createNew(String url, String label, Instant now) {
		return new RepertoireLink(null, null, url, label, now);
	}

	/** Reconstroi um link ja existente (vindo da persistencia). */
	public static RepertoireLink reconstruct(Long id, Long repertoireItemId, String url, String label,
			Instant createdAt) {
		return new RepertoireLink(id, repertoireItemId, url, label, createdAt);
	}

	public Long id() {
		return id;
	}

	public Long repertoireItemId() {
		return repertoireItemId;
	}

	public String url() {
		return url;
	}

	public String label() {
		return label;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
