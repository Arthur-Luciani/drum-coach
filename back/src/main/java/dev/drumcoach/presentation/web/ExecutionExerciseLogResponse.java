package dev.drumcoach.presentation.web;

import java.time.Instant;

import dev.drumcoach.domain.ExecutionExerciseLog;

/** DTO de saida de {@code ExecutionExerciseLog} para a API REST. */
public record ExecutionExerciseLogResponse(Long id, Long exerciseId, Integer achievedBpm,
		Integer actualDurationSeconds, String notes, Instant createdAt) {

	public static ExecutionExerciseLogResponse from(ExecutionExerciseLog log) {
		return new ExecutionExerciseLogResponse(log.id(), log.exerciseId(), log.achievedBpm(),
				log.actualDurationSeconds(), log.notes(), log.createdAt());
	}
}
