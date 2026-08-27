package dev.drumcoach.presentation.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.drumcoach.application.CreateExecutionCommand;
import dev.drumcoach.application.CreateExecutionUseCase;
import dev.drumcoach.application.ExecutionExerciseLogCommand;
import dev.drumcoach.application.ListExecutionsUseCase;
import dev.drumcoach.domain.Execution;

/**
 * Adapter HTTP para a entidade Execution (+ seus ExecutionExerciseLog). Nunca toca em
 * {@code infra} diretamente - so monta o comando e chama o caso de uso, exatamente como
 * uma tool MCP faria.
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

	private final CreateExecutionUseCase createExecutionUseCase;
	private final ListExecutionsUseCase listExecutionsUseCase;

	public ExecutionController(CreateExecutionUseCase createExecutionUseCase,
			ListExecutionsUseCase listExecutionsUseCase) {
		this.createExecutionUseCase = createExecutionUseCase;
		this.listExecutionsUseCase = listExecutionsUseCase;
	}

	@PostMapping
	public ResponseEntity<ExecutionResponse> create(@RequestBody CreateExecutionRequest request) {
		List<ExecutionExerciseLogCommand> logs = request.logs() == null ? List.of()
				: request.logs().stream()
					.map(log -> new ExecutionExerciseLogCommand(log.exerciseId(), log.achievedBpm(),
							log.actualDurationSeconds(), log.notes()))
					.toList();
		Execution execution = createExecutionUseCase.execute(new CreateExecutionCommand(request.trainingId(),
				request.executionDate(), request.actualDurationMinutes(), request.feeling(),
				request.generalNotes(), request.goalId(), logs));
		return ResponseEntity.created(URI.create("/api/executions/" + execution.id()))
			.body(ExecutionResponse.from(execution));
	}

	@GetMapping
	public List<ExecutionResponse> list(@RequestParam(required = false) Long trainingId,
			@RequestParam(required = false) Long goalId) {
		return listExecutionsUseCase.execute(trainingId, goalId).stream().map(ExecutionResponse::from).toList();
	}
}
