package dev.drumcoach.presentation.web;

/**
 * Corpo de {@code PATCH /api/trainings/{id}}. Todo campo ausente/{@code null} mantem o
 * valor atual (inclusive {@code goalId}: {@code null} nao desvincula o treino da meta, so
 * nao mexe no vinculo).
 */
public record UpdateTrainingRequest(String name, String description, Integer targetDurationMinutes,
		Integer targetRepetitions, Long goalId, Integer orderIndex) {
}
