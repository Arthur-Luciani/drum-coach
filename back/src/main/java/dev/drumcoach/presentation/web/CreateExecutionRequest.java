package dev.drumcoach.presentation.web;

import java.time.LocalDate;
import java.util.List;

/** Corpo de {@code POST /api/executions}. */
public record CreateExecutionRequest(Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
		String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLogRequest> logs) {
}
