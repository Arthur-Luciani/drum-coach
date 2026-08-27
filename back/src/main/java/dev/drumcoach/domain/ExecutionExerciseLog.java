package dev.drumcoach.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de BPM/observacoes alcancados num exercicio especifico, dentro de uma
 * {@link Execution}. Linha filha sem auditoria propria (created_by/last_modified_by) - a
 * origem e a da {@link Execution} pai (ver ADR-0005).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.ExecutionExerciseLogEntity}).
 */
public final class ExecutionExerciseLog {

	private final Long id;
	private final Long executionId;
	private final Long exerciseId;
	private final Integer achievedBpm;
	private final Integer actualDurationSeconds;
	private final String notes;
	private final Instant createdAt;

	private ExecutionExerciseLog(Long id, Long executionId, Long exerciseId, Integer achievedBpm,
			Integer actualDurationSeconds, String notes, Instant createdAt) {
		this.id = id;
		this.executionId = executionId;
		this.exerciseId = Objects.requireNonNull(exerciseId, "exerciseId must not be null");
		this.achievedBpm = achievedBpm;
		this.actualDurationSeconds = actualDurationSeconds;
		this.notes = notes;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	/** Cria um novo log ainda sem id/executionId (a persistir junto da Execution pai). */
	public static ExecutionExerciseLog createNew(Long exerciseId, Integer achievedBpm, Integer actualDurationSeconds,
			String notes, Instant now) {
		return new ExecutionExerciseLog(null, null, exerciseId, achievedBpm, actualDurationSeconds, notes, now);
	}

	/** Reconstroi um log ja existente (vindo da persistencia). */
	public static ExecutionExerciseLog reconstruct(Long id, Long executionId, Long exerciseId, Integer achievedBpm,
			Integer actualDurationSeconds, String notes, Instant createdAt) {
		return new ExecutionExerciseLog(id, executionId, exerciseId, achievedBpm, actualDurationSeconds, notes,
				createdAt);
	}

	public Long id() {
		return id;
	}

	public Long executionId() {
		return executionId;
	}

	public Long exerciseId() {
		return exerciseId;
	}

	public Integer achievedBpm() {
		return achievedBpm;
	}

	public Integer actualDurationSeconds() {
		return actualDurationSeconds;
	}

	public String notes() {
		return notes;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
