package dev.drumcoach.application;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Comando de entrada para {@link UpdateExerciseUseCase} (PATCH parcial de um exercicio).
 * Todo campo {@code null} mantem o valor atual.
 *
 * {@code kind} <strong>nao e editavel</strong> - so vem aqui para detectar uma tentativa de
 * troca: se vier diferente do atual, o use case rejeita com 400; {@code null} ou igual ao
 * atual e aceito. {@code pattern}, quando presente, e o documento JSON ja serializado como
 * string e e re-validado por {@link DrumPattern}.
 */
public record UpdateExerciseCommand(long id, ExerciseKind kind, String name, String exerciseType, String howToExecute,
		String pattern, Integer targetBpm, Integer targetDurationSeconds, VideoSourceType videoSourceType,
		String videoUrl, String videoFilePath, Integer orderIndex) {
}
