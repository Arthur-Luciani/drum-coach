package dev.drumcoach.infra.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

/** Repositorio tecnico Spring Data JDBC - detalhe de implementacao de {@code infra}. */
interface SpringDataLessonRepository extends CrudRepository<LessonEntity, Long> {

	List<LessonEntity> findByLessonDateBetween(LocalDate from, LocalDate to);
}
