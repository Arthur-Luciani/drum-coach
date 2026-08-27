package dev.drumcoach.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Lesson;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: registrar uma aula. A origem da escrita (USER|CLAUDE) e resolvida via
 * {@link OriginProvider} - nunca recebida do chamador (controller REST ou tool MCP).
 */
@Component
public class CreateLessonUseCase {

	private final LessonRepository lessonRepository;
	private final OriginProvider originProvider;

	public CreateLessonUseCase(LessonRepository lessonRepository, OriginProvider originProvider) {
		this.lessonRepository = lessonRepository;
		this.originProvider = originProvider;
	}

	public Lesson execute(CreateLessonCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Lesson lesson = Lesson.createNew(command.lessonDate(), command.teacherNotes(), command.feedback(),
				command.focusUntilNext(), command.suggestedMaterial(), command.generatedTrainingId(), origin, now);
		return lessonRepository.save(lesson);
	}
}
