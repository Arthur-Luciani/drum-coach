package dev.drumcoach.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Meta de treino do baterista (ex.: "tocar um solo de 2 minutos a 140 BPM").
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.GoalEntity}).
 */
public final class Goal {

	private final Long id;
	private final String title;
	private final String description;
	private final LocalDate targetDate;
	private final GoalStatus status;
	private final String targetMetric;
	private final boolean inFocus;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Goal(Long id, String title, String description, LocalDate targetDate, GoalStatus status,
			String targetMetric, boolean inFocus, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.title = Objects.requireNonNull(title, "title must not be null");
		this.description = description;
		this.targetDate = targetDate;
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.targetMetric = targetMetric;
		this.inFocus = inFocus;
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/** Cria uma nova meta ainda sem id (a persistir). Comeca sempre fora de foco. */
	public static Goal createNew(String title, String description, LocalDate targetDate, String targetMetric,
			Origin origin, Instant now) {
		return new Goal(null, title, description, targetDate, GoalStatus.NOT_STARTED, targetMetric, false, origin,
				origin, now, now);
	}

	/** Reconstroi uma meta ja existente (vinda da persistencia). */
	public static Goal reconstruct(Long id, String title, String description, LocalDate targetDate,
			GoalStatus status, String targetMetric, boolean inFocus, Origin createdBy, Origin lastModifiedBy,
			Instant createdAt, Instant updatedAt) {
		return new Goal(id, title, description, targetDate, status, targetMetric, inFocus, createdBy, lastModifiedBy,
				createdAt, updatedAt);
	}

	/**
	 * Aplica uma atualizacao parcial (PATCH) de progresso: campos {@code null} nos
	 * parametros mantem o valor atual. Nao mexe em {@code inFocus} (ver {@link #withFocus}).
	 * Retorna uma nova instancia (sem mutacao) com auditoria atualizada.
	 */
	public Goal withUpdate(GoalStatus status, String description, Origin editor, Instant now) {
		return new Goal(id, title, description != null ? description : this.description, targetDate,
				status != null ? status : this.status, targetMetric, inFocus, createdBy, editor, createdAt, now);
	}

	/**
	 * Marca/desmarca esta meta como "em foco" (Dashboard - ver ADR-0008). A aplicacao
	 * (nao o dominio) e responsavel por garantir que no maximo uma meta esteja em foco por
	 * vez, desfocando as demais na mesma transacao antes de chamar este metodo com
	 * {@code true} (ver {@code UpdateGoalUseCase}). Retorna uma nova instancia (sem
	 * mutacao) com auditoria atualizada.
	 */
	public Goal withFocus(boolean inFocus, Origin editor, Instant now) {
		return new Goal(id, title, description, targetDate, status, targetMetric, inFocus, createdBy, editor,
				createdAt, now);
	}

	public Long id() {
		return id;
	}

	public String title() {
		return title;
	}

	public String description() {
		return description;
	}

	public LocalDate targetDate() {
		return targetDate;
	}

	public GoalStatus status() {
		return status;
	}

	public String targetMetric() {
		return targetMetric;
	}

	public boolean inFocus() {
		return inFocus;
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
