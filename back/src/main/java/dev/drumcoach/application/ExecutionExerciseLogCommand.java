package dev.drumcoach.application;

/** Item de log de exercicio dentro de {@link CreateExecutionCommand}. */
public record ExecutionExerciseLogCommand(Long exerciseId, Integer achievedBpm, Integer actualDurationSeconds,
		String notes) {
}
