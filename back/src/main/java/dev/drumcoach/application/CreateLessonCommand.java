package dev.drumcoach.application;

import java.time.LocalDate;

/** Comando de entrada para {@link CreateLessonUseCase}. */
public record CreateLessonCommand(LocalDate lessonDate, String teacherNotes, String feedback,
		String focusUntilNext, String suggestedMaterial, Long generatedTrainingId) {
}
