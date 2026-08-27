package dev.drumcoach.application;

import dev.drumcoach.domain.ExerciseKind;

/**
 * Comando de entrada para {@link UpdateExerciseUseCase} (PATCH parcial de um exercicio).
 * Edita {@code pattern} e/ou {@code howToExecute}: um campo {@code null} mantem o valor
 * atual.
 *
 * {@code kind} <strong>nao e editavel</strong> - so vem aqui para detectar uma tentativa de
 * troca: se vier diferente do atual, o use case rejeita com 400; {@code null} ou igual ao
 * atual e aceito.
 */
public record UpdateExerciseCommand(long id, ExerciseKind kind, String pattern, String howToExecute) {
}
