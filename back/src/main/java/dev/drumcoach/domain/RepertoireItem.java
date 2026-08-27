package dev.drumcoach.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Musica do repertorio em aprendizado, com seus links de referencia ({@link
 * RepertoireLink}).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.RepertoireItemEntity}).
 */
public final class RepertoireItem {

	private final Long id;
	private final String songTitle;
	private final String artist;
	private final RepertoireItemStatus status;
	private final Integer targetBpm;
	private final Integer currentBpm;
	private final String notes;
	private final List<RepertoireLink> links;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private RepertoireItem(Long id, String songTitle, String artist, RepertoireItemStatus status, Integer targetBpm,
			Integer currentBpm, String notes, List<RepertoireLink> links, Origin createdBy, Origin lastModifiedBy,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.songTitle = Objects.requireNonNull(songTitle, "songTitle must not be null");
		this.artist = artist;
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.targetBpm = targetBpm;
		this.currentBpm = currentBpm;
		this.notes = notes;
		this.links = List.copyOf(links);
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/**
	 * Cria um novo item de repertorio ainda sem id (a persistir), com seus links. Comeca
	 * sempre em {@link RepertoireItemStatus#NOT_STARTED}.
	 */
	public static RepertoireItem createNew(String songTitle, String artist, Integer targetBpm, Integer currentBpm,
			String notes, List<RepertoireLink> links, Origin origin, Instant now) {
		return new RepertoireItem(null, songTitle, artist, RepertoireItemStatus.NOT_STARTED, targetBpm, currentBpm,
				notes, links, origin, origin, now, now);
	}

	/** Reconstroi um item ja existente (vindo da persistencia), com seus links. */
	public static RepertoireItem reconstruct(Long id, String songTitle, String artist, RepertoireItemStatus status,
			Integer targetBpm, Integer currentBpm, String notes, List<RepertoireLink> links, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		return new RepertoireItem(id, songTitle, artist, status, targetBpm, currentBpm, notes, links, createdBy,
				lastModifiedBy, createdAt, updatedAt);
	}

	/**
	 * Aplica uma atualizacao parcial (PATCH): campos {@code null} nos parametros mantem o
	 * valor atual; {@code newLinks} e sempre concatenado aos links existentes (nunca
	 * remove/edita um link ja persistido). Retorna uma nova instancia (sem mutacao) com
	 * auditoria atualizada.
	 */
	public RepertoireItem withUpdate(RepertoireItemStatus status, Integer currentBpm, String notes,
			List<RepertoireLink> newLinks, Origin editor, Instant now) {
		List<RepertoireLink> mergedLinks = newLinks.isEmpty() ? this.links
				: java.util.stream.Stream.concat(this.links.stream(), newLinks.stream()).toList();
		return new RepertoireItem(id, songTitle, artist, status != null ? status : this.status, targetBpm,
				currentBpm != null ? currentBpm : this.currentBpm, notes != null ? notes : this.notes, mergedLinks,
				createdBy, editor, createdAt, now);
	}

	public Long id() {
		return id;
	}

	public String songTitle() {
		return songTitle;
	}

	public String artist() {
		return artist;
	}

	public RepertoireItemStatus status() {
		return status;
	}

	public Integer targetBpm() {
		return targetBpm;
	}

	public Integer currentBpm() {
		return currentBpm;
	}

	public String notes() {
		return notes;
	}

	public List<RepertoireLink> links() {
		return links;
	}

	public Origin createdBy() {
		return createdBy;
	}

	public Origin lastModifiedBy() {
		return lastModifiedBy;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
