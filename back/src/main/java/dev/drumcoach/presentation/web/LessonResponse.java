package dev.drumcoach.presentation.web;

import java.time.Instant;
import java.time.LocalDate;

import dev.drumcoach.domain.Lesson;
import dev.drumcoach.domain.Origin;

/** DTO de saida de {@code Lesson} para a API REST. */
public record LessonResponse(Long id, LocalDate lessonDate, String teacherNotes, String feedback,
		String focusUntilNext, String suggestedMaterial, Long generatedTrainingId, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {

	public static LessonResponse from(Lesson lesson) {
		return new LessonResponse(lesson.id(), lesson.lessonDate(), lesson.teacherNotes(), lesson.feedback(),
				lesson.focusUntilNext(), lesson.suggestedMaterial(), lesson.generatedTrainingId(), lesson.createdBy(),
				lesson.lastModifiedBy(), lesson.createdAt(), lesson.updatedAt());
	}
}
