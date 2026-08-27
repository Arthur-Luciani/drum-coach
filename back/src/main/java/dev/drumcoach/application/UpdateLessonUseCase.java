package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Lesson;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: vincular a uma aula o treino gerado a partir dela
 * ({@code generatedTrainingId}). A origem da escrita (USER|CLAUDE) e resolvida via
 * {@link OriginProvider} - nunca recebida do chamador (controller REST ou tool MCP).
 */
@Component
public class UpdateLessonUseCase {

	private final LessonRepository lessonRepository;
	private final OriginProvider originProvider;

	public UpdateLessonUseCase(LessonRepository lessonRepository, OriginProvider originProvider) {
		this.lessonRepository = lessonRepository;
		this.originProvider = originProvider;
	}

	public Lesson execute(LinkGeneratedTrainingCommand command) {
		Lesson current = lessonRepository.findById(command.id())
			.orElseThrow(() -> new NoSuchElementException("Lesson nao encontrada: " + command.id()));
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Lesson updated = current.withGeneratedTraining(command.generatedTrainingId(), origin, now);
		return lessonRepository.save(updated);
	}
}
