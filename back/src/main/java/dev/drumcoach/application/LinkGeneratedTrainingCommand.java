package dev.drumcoach.application;

/**
 * Comando de entrada para {@link UpdateLessonUseCase} (PATCH parcial): vincula a aula ao
 * treino gerado a partir dela.
 */
public record LinkGeneratedTrainingCommand(long id, long generatedTrainingId) {
}
