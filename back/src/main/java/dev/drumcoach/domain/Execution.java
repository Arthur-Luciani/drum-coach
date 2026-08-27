package dev.drumcoach.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Execucao real de um {@link Training}: quando o baterista efetivamente treinou, com os
 * logs de BPM alcancado por exercicio ({@link ExecutionExerciseLog}). Agregado: a lista de
 * logs e persistida/lida sempre junto da execucao pai (ver ADR-0005 - os logs nao tem
 * auditoria propria).
 *
 * {@code trainingId} e nullable: uma "sessao livre" (Modo Sessao sem treino escolhido - so
 * cronometro/metronomo, sem lista de exercicios) gera uma execucao sem treino vinculado.
 * Nesse caso {@code logs} e sempre vazio (nao ha exercicio pra logar BPM/duracao).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.ExecutionEntity}).
 */
public final class Execution {

	private final Long id;
	private final Long trainingId;
	private final LocalDate executionDate;
	private final Integer actualDurationMinutes;
	private final String feeling;
	private final String generalNotes;
	private final Long goalId;
	private final List<ExecutionExerciseLog> logs;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Execution(Long id, Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
			String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLog> logs, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.trainingId = trainingId;
		this.executionDate = Objects.requireNonNull(executionDate, "executionDate must not be null");
		this.actualDurationMinutes = actualDurationMinutes;
		this.feeling = feeling;
		this.generalNotes = generalNotes;
		this.goalId = goalId;
		this.logs = List.copyOf(logs);
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/** Cria uma nova execucao ainda sem id (a persistir), com seus logs de exercicio. */
	public static Execution createNew(Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
			String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLog> logs, Origin origin,
			Instant now) {
		return new Execution(null, trainingId, executionDate, actualDurationMinutes, feeling, generalNotes, goalId,
				logs, origin, origin, now, now);
	}

	/** Reconstroi uma execucao ja existente (vinda da persistencia), com seus logs. */
	public static Execution reconstruct(Long id, Long trainingId, LocalDate executionDate,
			Integer actualDurationMinutes, String feeling, String generalNotes, Long goalId,
			List<ExecutionExerciseLog> logs, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
			Instant updatedAt) {
		return new Execution(id, trainingId, executionDate, actualDurationMinutes, feeling, generalNotes, goalId,
				logs, createdBy, lastModifiedBy, createdAt, updatedAt);
	}

	public Long id() {
		return id;
	}

	public Long trainingId() {
		return trainingId;
	}

	public LocalDate executionDate() {
		return executionDate;
	}

	public Integer actualDurationMinutes() {
		return actualDurationMinutes;
	}

	public String feeling() {
		return feeling;
	}

	public String generalNotes() {
		return generalNotes;
	}

	public Long goalId() {
		return goalId;
	}

	public List<ExecutionExerciseLog> logs() {
		return logs;
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
