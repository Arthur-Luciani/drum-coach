package dev.drumcoach.presentation.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.drumcoach.application.CreateTrainingCommand;
import dev.drumcoach.application.CreateTrainingUseCase;
import dev.drumcoach.application.DeleteTrainingUseCase;
import dev.drumcoach.application.ListTrainingsUseCase;
import dev.drumcoach.application.UpdateTrainingCommand;
import dev.drumcoach.application.UpdateTrainingUseCase;
import dev.drumcoach.domain.Training;

/**
 * Adapter HTTP para a entidade Training. Um unico endpoint de criacao aceita {@code
 * goalId} opcional no corpo (nulo = treino avulso, sem meta associada - ver ADR-0009) -
 * mais simples de consumir pelo Angular do que dois formatos de endpoint separados. O
 * PATCH edita qualquer campo (campos nulos mantem o atual); o DELETE apaga o treino com
 * seus exercicios/trechos em cascata, e responde 409 se houver execucoes registradas. As
 * traducoes de {@code NoSuchElementException} -&gt; 404 e {@code IllegalStateException}
 * -&gt; 409 ficam no {@code GlobalExceptionHandler}. Nunca toca em {@code infra}
 * diretamente - so monta o comando e chama o caso de uso, exatamente como uma tool MCP
 * faria.
 */
@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

	private final CreateTrainingUseCase createTrainingUseCase;
	private final ListTrainingsUseCase listTrainingsUseCase;
	private final UpdateTrainingUseCase updateTrainingUseCase;
	private final DeleteTrainingUseCase deleteTrainingUseCase;

	public TrainingController(CreateTrainingUseCase createTrainingUseCase,
			ListTrainingsUseCase listTrainingsUseCase, UpdateTrainingUseCase updateTrainingUseCase,
			DeleteTrainingUseCase deleteTrainingUseCase) {
		this.createTrainingUseCase = createTrainingUseCase;
		this.listTrainingsUseCase = listTrainingsUseCase;
		this.updateTrainingUseCase = updateTrainingUseCase;
		this.deleteTrainingUseCase = deleteTrainingUseCase;
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

	@PatchMapping("/{id}")
	public TrainingResponse update(@PathVariable long id, @RequestBody UpdateTrainingRequest request) {
		Training updated = updateTrainingUseCase.execute(new UpdateTrainingCommand(id, request.name(),
				request.description(), request.targetDurationMinutes(), request.targetRepetitions(), request.goalId(),
				request.orderIndex()));
		return TrainingResponse.from(updated);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		deleteTrainingUseCase.execute(id);
	}
}
