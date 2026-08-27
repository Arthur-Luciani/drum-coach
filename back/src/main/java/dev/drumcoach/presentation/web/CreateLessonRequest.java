package dev.drumcoach.presentation.web;

import java.time.LocalDate;

/** Corpo de {@code POST /api/lessons}. */
public record CreateLessonRequest(LocalDate lessonDate, String teacherNotes, String feedback,
		String focusUntilNext, String suggestedMaterial, Long generatedTrainingId) {
}
