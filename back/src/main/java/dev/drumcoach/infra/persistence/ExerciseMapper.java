package dev.drumcoach.infra.persistence;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExercisePassage;

/**
 * Mapeamento domain.Exercise (+ seus ExercisePassage) &lt;-&gt; registros de persistencia,
 * so via construtor/factory.
 */
final class ExerciseMapper {

	private ExerciseMapper() {
	}

	static ExerciseEntity toEntity(Exercise exercise) {
		return new ExerciseEntity(exercise.id(), exercise.trainingId(), exercise.name(), exercise.exerciseType(),
				exercise.kind(), exercise.howToExecute(), exercise.pattern(), exercise.targetBpm(),
				exercise.targetDurationSeconds(), exercise.videoSourceType(), exercise.videoUrl(),
				exercise.videoFilePath(), exercise.orderIndex(), exercise.createdBy(), exercise.lastModifiedBy(),
				exercise.createdAt(), exercise.updatedAt());
	}

	static Exercise toDomain(ExerciseEntity entity) {
		return Exercise.reconstruct(entity.id(), entity.trainingId(), entity.name(), entity.exerciseType(),
				entity.kind(), entity.howToExecute(), entity.pattern(), entity.targetBpm(),
				entity.targetDurationSeconds(), entity.videoSourceType(), entity.videoUrl(), entity.videoFilePath(),
				entity.orderIndex(), entity.createdBy(), entity.lastModifiedBy(), entity.createdAt(),
				entity.updatedAt());
	}

	static ExercisePassageEntity toEntity(ExercisePassage passage, long exerciseId) {
		return new ExercisePassageEntity(passage.id(), exerciseId, passage.fromSeconds(), passage.toSeconds(),
				passage.label(), passage.createdAt());
	}

	static ExercisePassage toDomain(ExercisePassageEntity entity) {
		return ExercisePassage.reconstruct(entity.id(), entity.exerciseId(), entity.fromSeconds(),
				entity.toSeconds(), entity.label(), entity.createdAt());
	}
}
