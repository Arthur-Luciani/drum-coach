package dev.drumcoach.infra.persistence;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

/** Repositorio tecnico Spring Data JDBC - detalhe de implementacao de {@code infra}. */
interface SpringDataExercisePassageRepository extends CrudRepository<ExercisePassageEntity, Long> {

	List<ExercisePassageEntity> findByExerciseIdOrderByFromSecondsAsc(long exerciseId);
}
