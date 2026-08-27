package dev.drumcoach.presentation.web;

/**
 * Corpo de {@code POST /api/trainings}. {@code goalId} nulo cria um treino avulso, sem
 * meta associada (ver ADR-0009).
 */
public record CreateTrainingRequest(Long goalId, String name, String description, int targetDurationMinutes,
		Integer targetRepetitions, int orderIndex) {
}
