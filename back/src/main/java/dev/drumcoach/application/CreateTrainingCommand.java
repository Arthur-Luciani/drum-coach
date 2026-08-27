package dev.drumcoach.application;

/**
 * Comando de entrada para {@link CreateTrainingUseCase}. {@code goalId} nulo cria um
 * treino avulso, sem meta associada (ver ADR-0009 - Treino pertence direto a Meta, sem
 * Plano intermediario).
 */
public record CreateTrainingCommand(Long goalId, String name, String description, int targetDurationMinutes,
		Integer targetRepetitions, int orderIndex) {
}
