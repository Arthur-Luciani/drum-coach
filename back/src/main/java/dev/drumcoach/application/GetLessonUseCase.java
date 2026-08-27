package dev.drumcoach.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Lesson;

/** Caso de uso: buscar uma aula por id. */
@Component
public class GetLessonUseCase {

	private final LessonRepository lessonRepository;

	public GetLessonUseCase(LessonRepository lessonRepository) {
		this.lessonRepository = lessonRepository;
	}

	public Optional<Lesson> execute(long id) {
		return lessonRepository.findById(id);
	}
}
