package dev.drumcoach.infra.persistence;

import dev.drumcoach.domain.Training;

/** Mapeamento domain.Training <-> infra.persistence.TrainingEntity, so via construtor/factory. */
final class TrainingMapper {

	private TrainingMapper() {
	}

	static TrainingEntity toEntity(Training training) {
		return new TrainingEntity(training.id(), training.goalId(), training.name(), training.description(),
				training.targetDurationMinutes(), training.targetRepetitions(), training.orderIndex(),
				training.createdBy(), training.lastModifiedBy(), training.createdAt(), training.updatedAt());
	}

	static Training toDomain(TrainingEntity entity) {
		return Training.reconstruct(entity.id(), entity.goalId(), entity.name(), entity.description(),
				entity.targetDurationMinutes(), entity.targetRepetitions(), entity.orderIndex(), entity.createdBy(),
				entity.lastModifiedBy(), entity.createdAt(), entity.updatedAt());
	}
}
