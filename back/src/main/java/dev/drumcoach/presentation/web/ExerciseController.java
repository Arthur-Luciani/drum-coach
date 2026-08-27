package dev.drumcoach.presentation.web;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.drumcoach.application.AddMarkedPassageCommand;
import dev.drumcoach.application.AddMarkedPassageUseCase;
import dev.drumcoach.application.CreateExerciseCommand;
import dev.drumcoach.application.CreateExerciseUseCase;
import dev.drumcoach.application.DeleteMarkedPassageUseCase;
import dev.drumcoach.application.GetExerciseUseCase;
import dev.drumcoach.application.ListExercisesByTrainingUseCase;
import dev.drumcoach.application.UpdateExerciseCommand;
import dev.drumcoach.application.UpdateExerciseUseCase;
import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExercisePassage;

/**
 * Adapter HTTP para a entidade Exercise (+ seus ExercisePassage). A criacao/listagem fica
 * aninhada sob um treino ({@code /api/trainings/{trainingId}/exercises}); a leitura por
 * id, o PATCH e os trechos marcados ficam sob {@code /api/exercises/{id}}. Nunca toca em
 * {@code infra} diretamente - so monta o comando e chama o caso de uso, exatamente como
 * uma tool MCP faria.
 */
@RestController
public class ExerciseController {

	private final CreateExerciseUseCase createExerciseUseCase;
	private final ListExercisesByTrainingUseCase listExercisesByTrainingUseCase;
	private final GetExerciseUseCase getExerciseUseCase;
	private final UpdateExerciseUseCase updateExerciseUseCase;
	private final AddMarkedPassageUseCase addMarkedPassageUseCase;
	private final DeleteMarkedPassageUseCase deleteMarkedPassageUseCase;
	private final ObjectMapper objectMapper;

	public ExerciseController(CreateExerciseUseCase createExerciseUseCase,
			ListExercisesByTrainingUseCase listExercisesByTrainingUseCase, GetExerciseUseCase getExerciseUseCase,
			UpdateExerciseUseCase updateExerciseUseCase, AddMarkedPassageUseCase addMarkedPassageUseCase,
			DeleteMarkedPassageUseCase deleteMarkedPassageUseCase, ObjectMapper objectMapper) {
		this.createExerciseUseCase = createExerciseUseCase;
		this.listExercisesByTrainingUseCase = listExercisesByTrainingUseCase;
		this.getExerciseUseCase = getExerciseUseCase;
		this.updateExerciseUseCase = updateExerciseUseCase;
		this.addMarkedPassageUseCase = addMarkedPassageUseCase;
		this.deleteMarkedPassageUseCase = deleteMarkedPassageUseCase;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/api/trainings/{trainingId}/exercises")
	public ResponseEntity<ExerciseResponse> create(@PathVariable long trainingId,
			@RequestBody CreateExerciseRequest request) {
		Exercise exercise = createExerciseUseCase.execute(new CreateExerciseCommand(trainingId, request.name(),
				request.exerciseType(), request.kind(), request.howToExecute(), writeJson(request.pattern()),
				request.targetBpm(), request.targetDurationSeconds(), request.videoSourceType(), request.videoUrl(),
				request.videoFilePath(), request.orderIndex()));
		return ResponseEntity.created(URI.create("/api/exercises/" + exercise.id()))
			.body(ExerciseResponse.from(exercise));
	}

	@GetMapping("/api/trainings/{trainingId}/exercises")
	public List<ExerciseResponse> listByTraining(@PathVariable long trainingId) {
		return listExercisesByTrainingUseCase.execute(trainingId).stream().map(ExerciseResponse::from).toList();
	}

	@GetMapping("/api/exercises/{id}")
	public ExerciseResponse get(@PathVariable long id) {
		GetExerciseUseCase.Result result = getExerciseUseCase.execute(id);
		return ExerciseResponse.from(result.exercise(), result.passages());
	}

	@PatchMapping("/api/exercises/{id}")
	public ExerciseResponse update(@PathVariable long id, @RequestBody UpdateExerciseRequest request) {
		Exercise updated = updateExerciseUseCase.execute(
				new UpdateExerciseCommand(id, request.kind(), writeJson(request.pattern()), request.howToExecute()));
		return ExerciseResponse.from(updated, getExerciseUseCase.execute(id).passages());
	}

	@PostMapping("/api/exercises/{exerciseId}/passages")
	public ResponseEntity<MarkedPassageResponse> addPassage(@PathVariable long exerciseId,
			@RequestBody MarkedPassageRequest request) {
		ExercisePassage passage = addMarkedPassageUseCase.execute(new AddMarkedPassageCommand(exerciseId,
				request.fromSeconds(), request.toSeconds(), request.label()));
		return ResponseEntity
			.created(URI.create("/api/exercises/" + exerciseId + "/passages/" + passage.id()))
			.body(MarkedPassageResponse.from(passage));
	}

	@DeleteMapping("/api/exercises/{exerciseId}/passages/{passageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePassage(@PathVariable long exerciseId, @PathVariable long passageId) {
		deleteMarkedPassageUseCase.execute(exerciseId, passageId);
	}

	/** Serializa o objeto JSON do {@code pattern} recebido para string, ou {@code null}. */
	private String writeJson(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(node);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("pattern invalido: " + e.getMessage());
		}
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void handleNotFound() {
		// corpo vazio - so sinaliza 404 quando o exercicio/trecho nao existe.
	}
}
