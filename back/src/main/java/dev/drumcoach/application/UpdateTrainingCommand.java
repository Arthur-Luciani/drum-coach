package dev.drumcoach.application;

/**
 * Comando de entrada para {@link UpdateTrainingUseCase} (PATCH parcial de um treino). Todo
 * campo {@code null} mantem o valor atual - inclusive {@code goalId} ({@code null} nao
 * transforma o treino em avulso, so nao mexe no vinculo).
 */
public record UpdateTrainingCommand(long id, String name, String description, Integer targetDurationMinutes,
		Integer targetRepetitions, Long goalId, Integer orderIndex) {
}
