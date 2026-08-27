package dev.drumcoach.application;

import java.time.LocalDate;
import java.util.List;

/** Comando de entrada para {@link CreateExecutionUseCase}. */
public record CreateExecutionCommand(Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
		String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLogCommand> logs) {

	public CreateExecutionCommand {
		logs = logs == null ? List.of() : List.copyOf(logs);
	}
}
