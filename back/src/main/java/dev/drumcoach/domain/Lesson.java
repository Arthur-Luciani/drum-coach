package dev.drumcoach.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aula com o professor: o que foi passado, feedback recebido e o foco ate a proxima aula.
 * Pode opcionalmente gerar um {@link Training} ({@code generatedTrainingId}) - antes do
 * ADR-0009 apontava para um {@code TrainingPlan}, que deixou de existir; agora aponta
 * direto para o Treino criado a partir da aula.
 *
 * Entidade de dominio pura: sem anotacoes de Spring/JDBC. A camada {@code infra} e
 * responsavel por mapear esta classe de/para o registro de persistencia
 * ({@code infra.persistence.LessonEntity}).
 */
public final class Lesson {

	private final Long id;
	private final LocalDate lessonDate;
	private final String teacherNotes;
	private final String feedback;
	private final String focusUntilNext;
	private final String suggestedMaterial;
	private final Long generatedTrainingId;
	private final Origin createdBy;
	private final Origin lastModifiedBy;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Lesson(Long id, LocalDate lessonDate, String teacherNotes, String feedback, String focusUntilNext,
			String suggestedMaterial, Long generatedTrainingId, Origin createdBy, Origin lastModifiedBy,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.lessonDate = Objects.requireNonNull(lessonDate, "lessonDate must not be null");
		this.teacherNotes = Objects.requireNonNull(teacherNotes, "teacherNotes must not be null");
		this.feedback = feedback;
		this.focusUntilNext = focusUntilNext;
		this.suggestedMaterial = suggestedMaterial;
		this.generatedTrainingId = generatedTrainingId;
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		this.lastModifiedBy = Objects.requireNonNull(lastModifiedBy, "lastModifiedBy must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	/** Cria uma nova aula ainda sem id (a persistir). */
	public static Lesson createNew(LocalDate lessonDate, String teacherNotes, String feedback,
			String focusUntilNext, String suggestedMaterial, Long generatedTrainingId, Origin origin, Instant now) {
		return new Lesson(null, lessonDate, teacherNotes, feedback, focusUntilNext, suggestedMaterial,
				generatedTrainingId, origin, origin, now, now);
	}

	/** Reconstroi uma aula ja existente (vinda da persistencia). */
	public static Lesson reconstruct(Long id, LocalDate lessonDate, String teacherNotes, String feedback,
			String focusUntilNext, String suggestedMaterial, Long generatedTrainingId, Origin createdBy,
			Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
		return new Lesson(id, lessonDate, teacherNotes, feedback, focusUntilNext, suggestedMaterial,
				generatedTrainingId, createdBy, lastModifiedBy, createdAt, updatedAt);
	}

	/**
	 * Vincula esta aula ao treino gerado a partir dela ({@code generatedTrainingId}).
	 * Retorna uma nova instancia (sem mutacao) com auditoria atualizada.
	 */
	public Lesson withGeneratedTraining(Long generatedTrainingId, Origin editor, Instant now) {
		return new Lesson(id, lessonDate, teacherNotes, feedback, focusUntilNext, suggestedMaterial,
				generatedTrainingId, createdBy, editor, createdAt, now);
	}

	public Long id() {
		return id;
	}

	public LocalDate lessonDate() {
		return lessonDate;
	}

	public String teacherNotes() {
		return teacherNotes;
	}

	public String feedback() {
		return feedback;
	}

	public String focusUntilNext() {
		return focusUntilNext;
	}

	public String suggestedMaterial() {
		return suggestedMaterial;
	}

	public Long generatedTrainingId() {
		return generatedTrainingId;
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
