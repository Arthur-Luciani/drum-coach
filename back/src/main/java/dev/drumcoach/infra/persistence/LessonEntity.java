package dev.drumcoach.infra.persistence;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.Origin;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.Lesson}, mapeado via Spring
 * Data JDBC para a tabela {@code lesson}. Nunca e exposto fora de {@code infra} - o
 * domain.Lesson e mapeado de/para esta classe por {@link LessonMapper}.
 */
@Table("lesson")
public record LessonEntity(@Id Long id, LocalDate lessonDate, String teacherNotes, String feedback,
		String focusUntilNext, String suggestedMaterial, Long generatedTrainingId, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
}
