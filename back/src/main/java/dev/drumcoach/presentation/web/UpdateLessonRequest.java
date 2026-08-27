package dev.drumcoach.presentation.web;

/** Corpo de {@code PATCH /api/lessons/{id}}: vincula o treino gerado a partir da aula. */
public record UpdateLessonRequest(Long generatedTrainingId) {
}
