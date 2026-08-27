package dev.drumcoach.presentation.web;

import java.time.Instant;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.Training;

/** DTO de saida de {@code Training} para a API REST. */
public record TrainingResponse(Long id, Long goalId, String name, String description, int targetDurationMinutes,
		Integer targetRepetitions, int orderIndex, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
		Instant updatedAt) {

	public static TrainingResponse from(Training training) {
		return new TrainingResponse(training.id(), training.goalId(), training.name(), training.description(),
				training.targetDurationMinutes(), training.targetRepetitions(), training.orderIndex(),
				training.createdBy(), training.lastModifiedBy(), training.createdAt(), training.updatedAt());
	}
}
