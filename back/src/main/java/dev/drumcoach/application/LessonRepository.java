package dev.drumcoach.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.Lesson;

/**
 * Port de persistencia de {@link Lesson}, implementado por {@code infra} (Spring Data
 * JDBC). A camada {@code application} so conhece esta interface - nunca depende
 * diretamente de Spring Data/JDBC.
 */
public interface LessonRepository {

	Lesson save(Lesson lesson);

	List<Lesson> findAll();

	List<Lesson> findByLessonDateBetween(LocalDate from, LocalDate to);

	Optional<Lesson> findById(long id);
}
