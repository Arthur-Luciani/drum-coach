package dev.drumcoach.presentation.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.drumcoach.application.CreateLessonCommand;
import dev.drumcoach.application.CreateLessonUseCase;
import dev.drumcoach.application.GetLessonUseCase;
import dev.drumcoach.application.LinkGeneratedTrainingCommand;
import dev.drumcoach.application.ListLessonsUseCase;
import dev.drumcoach.application.UpdateLessonUseCase;
import dev.drumcoach.domain.Lesson;

/**
 * Adapter HTTP para a entidade Lesson. Nunca toca em {@code infra} diretamente - so monta
 * o comando e chama o caso de uso, exatamente como uma tool MCP faria.
 */
@RestController
@RequestMapping("/api/lessons")
public class LessonController {

	private final CreateLessonUseCase createLessonUseCase;
	private final ListLessonsUseCase listLessonsUseCase;
	private final GetLessonUseCase getLessonUseCase;
	private final UpdateLessonUseCase updateLessonUseCase;

	public LessonController(CreateLessonUseCase createLessonUseCase, ListLessonsUseCase listLessonsUseCase,
			GetLessonUseCase getLessonUseCase, UpdateLessonUseCase updateLessonUseCase) {
		this.createLessonUseCase = createLessonUseCase;
		this.listLessonsUseCase = listLessonsUseCase;
		this.getLessonUseCase = getLessonUseCase;
		this.updateLessonUseCase = updateLessonUseCase;
	}

	@PostMapping
	public ResponseEntity<LessonResponse> create(@RequestBody CreateLessonRequest request) {
		Lesson lesson = createLessonUseCase.execute(new CreateLessonCommand(request.lessonDate(),
				request.teacherNotes(), request.feedback(), request.focusUntilNext(), request.suggestedMaterial(),
				request.generatedTrainingId()));
		return ResponseEntity.created(URI.create("/api/lessons/" + lesson.id())).body(LessonResponse.from(lesson));
	}

	@GetMapping
	public List<LessonResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return listLessonsUseCase.execute(from, to).stream().map(LessonResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<LessonResponse> getById(@PathVariable long id) {
		return getLessonUseCase.execute(id)
			.map(lesson -> ResponseEntity.ok(LessonResponse.from(lesson)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/{id}")
	public LessonResponse update(@PathVariable long id, @RequestBody UpdateLessonRequest request) {
		Lesson updated = updateLessonUseCase.execute(new LinkGeneratedTrainingCommand(id, request.generatedTrainingId()));
		return LessonResponse.from(updated);
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void handleNotFound() {
		// corpo vazio - so sinaliza 404 quando a aula nao existe.
	}
}
