package dev.drumcoach.presentation.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.drumcoach.domain.Execution;
import dev.drumcoach.domain.Origin;

/** DTO de saida de {@code Execution} (+ seus logs de exercicio) para a API REST. */
public record ExecutionResponse(Long id, Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
		String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLogResponse> logs, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {

	public static ExecutionResponse from(Execution execution) {
		List<ExecutionExerciseLogResponse> logs = execution.logs().stream()
			.map(ExecutionExerciseLogResponse::from)
			.toList();
		return new ExecutionResponse(execution.id(), execution.trainingId(), execution.executionDate(),
				execution.actualDurationMinutes(), execution.feeling(), execution.generalNotes(),
				execution.goalId(), logs, execution.createdBy(), execution.lastModifiedBy(), execution.createdAt(),
				execution.updatedAt());
	}
}
