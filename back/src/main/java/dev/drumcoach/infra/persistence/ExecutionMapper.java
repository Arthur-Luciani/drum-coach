package dev.drumcoach.infra.persistence;

import java.util.List;

import dev.drumcoach.domain.Execution;
import dev.drumcoach.domain.ExecutionExerciseLog;

/**
 * Mapeamento domain.Execution (+ seus ExecutionExerciseLog) <-> registros de
 * persistencia, so via construtor/factory.
 */
final class ExecutionMapper {

	private ExecutionMapper() {
	}

	static ExecutionEntity toEntity(Execution execution) {
		return new ExecutionEntity(execution.id(), execution.trainingId(), execution.executionDate(),
				execution.actualDurationMinutes(), execution.feeling(), execution.generalNotes(),
				execution.goalId(), execution.createdBy(), execution.lastModifiedBy(), execution.createdAt(),
				execution.updatedAt());
	}

	static ExecutionExerciseLogEntity toEntity(ExecutionExerciseLog log, long executionId) {
		return new ExecutionExerciseLogEntity(log.id(), executionId, log.exerciseId(), log.achievedBpm(),
				log.actualDurationSeconds(), log.notes(), log.createdAt());
	}

	static ExecutionExerciseLog toDomain(ExecutionExerciseLogEntity entity) {
		return ExecutionExerciseLog.reconstruct(entity.id(), entity.executionId(), entity.exerciseId(),
				entity.achievedBpm(), entity.actualDurationSeconds(), entity.notes(), entity.createdAt());
	}

	static Execution toDomain(ExecutionEntity entity, List<ExecutionExerciseLog> logs) {
		return Execution.reconstruct(entity.id(), entity.trainingId(), entity.executionDate(),
				entity.actualDurationMinutes(), entity.feeling(), entity.generalNotes(), entity.goalId(), logs,
				entity.createdBy(), entity.lastModifiedBy(), entity.createdAt(), entity.updatedAt());
	}
}
