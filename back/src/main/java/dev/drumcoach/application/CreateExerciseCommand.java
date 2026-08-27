package dev.drumcoach.application;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Comando de entrada para {@link CreateExerciseUseCase}. {@code pattern} e o documento JSON
 * do padrao tocavel ja serializado como string (so faz sentido em {@code kind ==
 * TOCA_JUNTO}); {@code null}/vazio = sem padrao.
 */
public record CreateExerciseCommand(Long trainingId, String name, String exerciseType, ExerciseKind kind,
		String howToExecute, String pattern, Integer targetBpm, Integer targetDurationSeconds,
		VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex) {
}
