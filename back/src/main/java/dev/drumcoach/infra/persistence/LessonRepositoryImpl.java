package dev.drumcoach.infra.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Repository;

import dev.drumcoach.application.LessonRepository;
import dev.drumcoach.domain.Lesson;

/** Implementacao do port {@link LessonRepository} via Spring Data JDBC. */
@Repository
public class LessonRepositoryImpl implements LessonRepository {

	private final SpringDataLessonRepository jdbcRepository;

	public LessonRepositoryImpl(SpringDataLessonRepository jdbcRepository) {
		this.jdbcRepository = jdbcRepository;
	}

	@Override
	public Lesson save(Lesson lesson) {
		LessonEntity saved = jdbcRepository.save(LessonMapper.toEntity(lesson));
		return LessonMapper.toDomain(saved);
	}

	@Override
	public List<Lesson> findAll() {
		return StreamSupport.stream(jdbcRepository.findAll().spliterator(), false).map(LessonMapper::toDomain).toList();
	}

	@Override
	public List<Lesson> findByLessonDateBetween(LocalDate from, LocalDate to) {
		return jdbcRepository.findByLessonDateBetween(from, to).stream().map(LessonMapper::toDomain).toList();
	}

	@Override
	public Optional<Lesson> findById(long id) {
		return jdbcRepository.findById(id).map(LessonMapper::toDomain);
	}
}
