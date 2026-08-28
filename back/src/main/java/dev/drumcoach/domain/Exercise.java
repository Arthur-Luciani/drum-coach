package dev.drumcoach.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Exercicio dentro de um {@link Training} (ex.: um rudimento, um groove, um trecho de
 * leitura). {@code exerciseType} e texto livre no MVP (sem taxonomia fechada).
 *
 * O eixo {@link ExerciseKind} (ver ADR-0011) decide como o exercicio e executado:
 * {@code TOCA_JUNTO} tem um {@code pattern} tocavel (JSON, validado por
 * {@code application.DrumPattern}); {@code TRANSCRICAO} usa notas livres
 * ({@code howToExecute}) + trechos marcados ({@link ExercisePassage}). Regra de
 * consistencia: {@code pattern} nao-nulo so e valido quando {@code kind == TOCA_JUNTO}. Um
 * {@code TOCA_JUNTO} pode nascer sem pattern e recebe-lo depois via PATCH (o editor da
 * grade monta o documento inteiro incrementalmente).
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.ExerciseEntity}).
 */
public final class Exercise {

	private final Long id;
	private final Long trainingId;
	private final String name;
	private final String exerciseType;
	private final ExerciseKind kind;
	private final String howToExecute;
	private final String pattern;
	private final Integer targetBpm;
	private final Integer targetDurationSeconds;
	private final VideoSourceType videoSourceType;
	private final String videoUrl;
	private final String videoFilePath;
	private final int orderIndex;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Exercise(Long id, Long trainingId, String name, String exerciseType, ExerciseKind kind,
			String howToExecute, String pattern, Integer targetBpm, Integer targetDurationSeconds,
			VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.trainingId = Objects.requireNonNull(trainingId, "trainingId must not be null");
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.exerciseType = Objects.requireNonNull(exerciseType, "exerciseType must not be null");
		this.kind = Objects.requireNonNull(kind, "kind must not be null");
		this.howToExecute = howToExecute;
		if (pattern != null && kind != ExerciseKind.TOCA_JUNTO) {
			throw new IllegalArgumentException(
					"pattern so e valido em exercicios do tipo TOCA_JUNTO (kind atual: " + kind + ")");
		}
		this.pattern = pattern;
		this.targetBpm = targetBpm;
		this.targetDurationSeconds = targetDurationSeconds;
		this.videoSourceType = videoSourceType;
		this.videoUrl = videoUrl;
		this.videoFilePath = videoFilePath;
		this.orderIndex = orderIndex;
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/** Cria um novo exercicio ainda sem id (a persistir). */
	public static Exercise createNew(Long trainingId, String name, String exerciseType, ExerciseKind kind,
			String howToExecute, String pattern, Integer targetBpm, Integer targetDurationSeconds,
			VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex, Origin origin,
			Instant now) {
		return new Exercise(null, trainingId, name, exerciseType, kind, howToExecute, pattern, targetBpm,
				targetDurationSeconds, videoSourceType, videoUrl, videoFilePath, orderIndex, origin, origin, now, now);
	}

	/** Reconstroi um exercicio ja existente (vindo da persistencia). */
	public static Exercise reconstruct(Long id, Long trainingId, String name, String exerciseType, ExerciseKind kind,
			String howToExecute, String pattern, Integer targetBpm, Integer targetDurationSeconds,
			VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		return new Exercise(id, trainingId, name, exerciseType, kind, howToExecute, pattern, targetBpm,
				targetDurationSeconds, videoSourceType, videoUrl, videoFilePath, orderIndex, createdBy, lastModifiedBy,
				createdAt, updatedAt);
	}

	/**
	 * Aplica uma atualizacao parcial (PATCH) de qualquer campo editavel do exercicio: cada
	 * parametro {@code null} mantem o valor atual. {@code kind} nunca muda aqui (e imutavel
	 * - ver {@code UpdateExerciseUseCase}); {@code newPattern}, quando presente, ja deve vir
	 * validado/canonico (ver {@code application.DrumPattern}). Retorna uma nova instancia
	 * (sem mutacao) com auditoria atualizada.
	 */
	public Exercise withUpdated(String newName, String newExerciseType, String newHowToExecute, String newPattern,
			Integer newTargetBpm, Integer newTargetDurationSeconds, VideoSourceType newVideoSourceType,
			String newVideoUrl, String newVideoFilePath, Integer newOrderIndex, Origin editor, Instant now) {
		return new Exercise(id, trainingId, newName != null ? newName : this.name,
				newExerciseType != null ? newExerciseType : this.exerciseType, kind,
				newHowToExecute != null ? newHowToExecute : this.howToExecute,
				newPattern != null ? newPattern : this.pattern,
				newTargetBpm != null ? newTargetBpm : this.targetBpm,
				newTargetDurationSeconds != null ? newTargetDurationSeconds : this.targetDurationSeconds,
				newVideoSourceType != null ? newVideoSourceType : this.videoSourceType,
				newVideoUrl != null ? newVideoUrl : this.videoUrl,
				newVideoFilePath != null ? newVideoFilePath : this.videoFilePath,
				newOrderIndex != null ? newOrderIndex : this.orderIndex, createdBy, editor, createdAt, now);
	}

	public Long id() {
		return id;
	}

	public Long trainingId() {
		return trainingId;
	}

	public String name() {
		return name;
	}

	public String exerciseType() {
		return exerciseType;
	}

	public ExerciseKind kind() {
		return kind;
	}

	public String howToExecute() {
		return howToExecute;
	}

	public String pattern() {
		return pattern;
	}

	public Integer targetBpm() {
		return targetBpm;
	}

	public Integer targetDurationSeconds() {
		return targetDurationSeconds;
	}

	public VideoSourceType videoSourceType() {
		return videoSourceType;
	}

	public String videoUrl() {
		return videoUrl;
	}

	public String videoFilePath() {
		return videoFilePath;
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
