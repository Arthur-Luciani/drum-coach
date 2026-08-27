package dev.drumcoach.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Trecho marcado de uma {@link Exercise} do tipo {@link ExerciseKind#TRANSCRICAO}:
 * timestamp de inicio ({@code fromSeconds}), timestamp de fim opcional ({@code toSeconds} -
 * nulo = marcacao pontual) e um rotulo curto opcional. Linha filha sem auditoria propria
 * (created_by/last_modified_by) - a origem e a do {@link Exercise} pai (ver ADR-0005).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.ExercisePassageEntity}).
 */
public final class ExercisePassage {

	private final Long id;
	private final Long exerciseId;
	private final int fromSeconds;
	private final Integer toSeconds;
	private final String label;
	private final Instant createdAt;

	private ExercisePassage(Long id, Long exerciseId, int fromSeconds, Integer toSeconds, String label,
			Instant createdAt) {
		this.id = id;
		this.exerciseId = exerciseId;
		if (fromSeconds < 0) {
			throw new IllegalArgumentException("fromSeconds deve ser >= 0, mas e " + fromSeconds);
		}
		this.fromSeconds = fromSeconds;
		if (toSeconds != null && toSeconds < fromSeconds) {
			throw new IllegalArgumentException(
					"toSeconds (" + toSeconds + ") deve ser >= fromSeconds (" + fromSeconds + ")");
		}
		this.toSeconds = toSeconds;
		this.label = label;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	/** Cria um novo trecho ainda sem id/exerciseId (a persistir junto do exercicio pai). */
	public static ExercisePassage createNew(int fromSeconds, Integer toSeconds, String label, Instant now) {
		return new ExercisePassage(null, null, fromSeconds, toSeconds, label, now);
	}

	/** Reconstroi um trecho ja existente (vindo da persistencia). */
	public static ExercisePassage reconstruct(Long id, Long exerciseId, int fromSeconds, Integer toSeconds,
			String label, Instant createdAt) {
		return new ExercisePassage(id, exerciseId, fromSeconds, toSeconds, label, createdAt);
	}

	public Long id() {
		return id;
	}

	public Long exerciseId() {
		return exerciseId;
	}

	public int fromSeconds() {
		return fromSeconds;
	}

	public Integer toSeconds() {
		return toSeconds;
	}

	public String label() {
		return label;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
