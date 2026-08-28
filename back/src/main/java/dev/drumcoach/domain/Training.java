package dev.drumcoach.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Treino (template de execucao): um conjunto ordenado de exercicios. Pertence direto a
 * uma {@link Goal} ({@code goalId} preenchido) ou e um treino avulso, sem meta associada
 * ({@code goalId} nulo) - ver ADR-0009 (nao existe mais um "Plano" intermediario entre
 * Meta e Treino).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.TrainingEntity}).
 */
public final class Training {

	private final Long id;
	private final Long goalId;
	private final String name;
	private final String description;
	private final int targetDurationMinutes;
	private final Integer targetRepetitions;
	private final int orderIndex;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Training(Long id, Long goalId, String name, String description, int targetDurationMinutes,
			Integer targetRepetitions, int orderIndex, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.goalId = goalId;
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.description = description;
		this.targetDurationMinutes = targetDurationMinutes;
		this.targetRepetitions = targetRepetitions;
		this.orderIndex = orderIndex;
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/** Cria um novo treino ainda sem id (a persistir). */
	public static Training createNew(Long goalId, String name, String description, int targetDurationMinutes,
			Integer targetRepetitions, int orderIndex, Origin origin, Instant now) {
		return new Training(null, goalId, name, description, targetDurationMinutes, targetRepetitions, orderIndex,
				origin, origin, now, now);
	}

	/** Reconstroi um treino ja existente (vindo da persistencia). */
	public static Training reconstruct(Long id, Long goalId, String name, String description,
			int targetDurationMinutes, Integer targetRepetitions, int orderIndex, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		return new Training(id, goalId, name, description, targetDurationMinutes, targetRepetitions, orderIndex,
				createdBy, lastModifiedBy, createdAt, updatedAt);
	}

	/**
	 * Aplica uma atualizacao parcial (PATCH): cada parametro {@code null} mantem o valor
	 * atual. Retorna uma nova instancia (sem mutacao) com auditoria atualizada
	 * ({@code lastModifiedBy} = {@code editor}, {@code updatedAt} = {@code now}).
	 */
	public Training withUpdated(String name, String description, Integer targetDurationMinutes,
			Integer targetRepetitions, Long goalId, Integer orderIndex, Origin editor, Instant now) {
		return new Training(id, goalId != null ? goalId : this.goalId, name != null ? name : this.name,
				description != null ? description : this.description,
				targetDurationMinutes != null ? targetDurationMinutes : this.targetDurationMinutes,
				targetRepetitions != null ? targetRepetitions : this.targetRepetitions,
				orderIndex != null ? orderIndex : this.orderIndex, createdBy, editor, createdAt, now);
	}

	public Long id() {
		return id;
	}

	public Long goalId() {
		return goalId;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public int targetDurationMinutes() {
		return targetDurationMinutes;
	}

	public Integer targetRepetitions() {
		return targetRepetitions;
	}

	public int orderIndex() {
		return orderIndex;
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
