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

import dev.drumcoach.application.CreateTrainingCommand;
import dev.drumcoach.application.CreateTrainingUseCase;
import dev.drumcoach.application.ListTrainingsUseCase;
import dev.drumcoach.domain.Training;

/**
 * Adapter HTTP para a entidade Training. Um unico endpoint de criacao aceita {@code
 * goalId} opcional no corpo (nulo = treino avulso, sem meta associada - ver ADR-0009) -
 * mais simples de consumir pelo Angular do que dois formatos de endpoint separados. Nunca
 * toca em {@code infra} diretamente - so monta o comando e chama o caso de uso,
 * exatamente como uma tool MCP faria.
 */
@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

	private final CreateTrainingUseCase createTrainingUseCase;
	private final ListTrainingsUseCase listTrainingsUseCase;

	public TrainingController(CreateTrainingUseCase createTrainingUseCase,
			ListTrainingsUseCase listTrainingsUseCase) {
		this.createTrainingUseCase = createTrainingUseCase;
		this.listTrainingsUseCase = listTrainingsUseCase;
	}

	@PostMapping
	public ResponseEntity<TrainingResponse> create(@RequestBody CreateTrainingRequest request) {
		Training training = createTrainingUseCase.execute(new CreateTrainingCommand(request.goalId(),
				request.name(), request.description(), request.targetDurationMinutes(), request.targetRepetitions(),
				request.orderIndex()));
		return ResponseEntity.created(URI.create("/api/trainings/" + training.id()))
			.body(TrainingResponse.from(training));
	}

	@GetMapping
	public List<TrainingResponse> list(@RequestParam(required = false) Long goalId) {
		return listTrainingsUseCase.execute(goalId).stream().map(TrainingResponse::from).toList();
	}
}
