package dev.drumcoach.presentation.web;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.drumcoach.application.CreateGoalCommand;
import dev.drumcoach.application.CreateGoalUseCase;
import dev.drumcoach.application.GetGoalDetailUseCase;
import dev.drumcoach.application.ListGoalsUseCase;
import dev.drumcoach.application.UpdateGoalCommand;
import dev.drumcoach.application.UpdateGoalUseCase;
import dev.drumcoach.domain.Goal;

/**
 * Adapter HTTP para a entidade Goal. Nunca toca em {@code infra} diretamente - so monta o
 * comando e chama o caso de uso, exatamente como uma tool MCP faria.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

	private final CreateGoalUseCase createGoalUseCase;
	private final ListGoalsUseCase listGoalsUseCase;
	private final UpdateGoalUseCase updateGoalUseCase;
	private final GetGoalDetailUseCase getGoalDetailUseCase;

	public GoalController(CreateGoalUseCase createGoalUseCase, ListGoalsUseCase listGoalsUseCase,
			UpdateGoalUseCase updateGoalUseCase, GetGoalDetailUseCase getGoalDetailUseCase) {
		this.createGoalUseCase = createGoalUseCase;
		this.listGoalsUseCase = listGoalsUseCase;
		this.updateGoalUseCase = updateGoalUseCase;
		this.getGoalDetailUseCase = getGoalDetailUseCase;
	}

	@PostMapping
	public ResponseEntity<GoalResponse> create(@RequestBody CreateGoalRequest request) {
		Goal goal = createGoalUseCase
			.execute(new CreateGoalCommand(request.title(), request.description(), request.targetDate(),
					request.targetMetric()));
		return ResponseEntity.created(URI.create("/api/goals/" + goal.id())).body(GoalResponse.from(goal));
	}

	@GetMapping
	public List<GoalResponse> list() {
		return listGoalsUseCase.execute().stream().map(GoalResponse::from).toList();
	}

	/**
	 * Detalhe de uma meta: a propria meta + os treinos que pertencem direto a ela, cada
	 * um com o progresso (execucoes registradas vs. {@code targetRepetitions}) - ver
	 * ADR-0009. Substitui o antigo {@code GET /api/plans/{id}}.
	 */
	@GetMapping("/{id}")
	public ResponseEntity<GoalDetailResponse> getById(@PathVariable long id) {
		return getGoalDetailUseCase.execute(id)
			.map(detail -> ResponseEntity.ok(GoalDetailResponse.from(detail)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/{id}")
	public GoalResponse update(@PathVariable long id, @RequestBody UpdateGoalRequest request) {
		Goal updated = updateGoalUseCase
			.execute(new UpdateGoalCommand(id, request.status(), request.description(), request.inFocus()));
		return GoalResponse.from(updated);
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void handleNotFound() {
		// corpo vazio - so sinaliza 404 quando a meta nao existe.
	}
}
