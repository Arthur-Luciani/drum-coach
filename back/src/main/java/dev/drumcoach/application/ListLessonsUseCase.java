package dev.drumcoach.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Lesson;

/** Caso de uso: listar aulas, opcionalmente filtradas por periodo ({@code from}/{@code to}). */
@Component
public class ListLessonsUseCase {

	private final LessonRepository lessonRepository;

	public ListLessonsUseCase(LessonRepository lessonRepository) {
		this.lessonRepository = lessonRepository;
	}

	public List<Lesson> execute(LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return lessonRepository.findByLessonDateBetween(from, to);
		}
		return lessonRepository.findAll();
	}
}
