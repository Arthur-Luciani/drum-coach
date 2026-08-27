package dev.drumcoach.presentation.web;

/** Item de log de exercicio dentro de {@link CreateExecutionRequest}. */
public record ExecutionExerciseLogRequest(Long exerciseId, Integer achievedBpm, Integer actualDurationSeconds,
		String notes) {
}
