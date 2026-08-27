package dev.drumcoach.infra.persistence;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.application.ExerciseRepository;
import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExercisePassage;

/**
 * Implementacao do port {@link ExerciseRepository} via Spring Data JDBC. {@code
 * ExerciseEntity} e {@code ExercisePassageEntity} sao tabelas flat separadas (nao um
 * agregado Spring Data JDBC nativo via {@code @MappedCollection}) - esta classe orquestra
 * explicitamente a raiz + os filhos, o que preserva o {@code created_at} de cada trecho ja
 * persistido (add/delete incrementais, sem reescrever a colecao inteira).
 */
@Repository
public class ExerciseRepositoryImpl implements ExerciseRepository {

	private final SpringDataExerciseRepository jdbcRepository;
	private final SpringDataExercisePassageRepository passageJdbcRepository;

	public ExerciseRepositoryImpl(SpringDataExerciseRepository jdbcRepository,
			SpringDataExercisePassageRepository passageJdbcRepository) {
		this.jdbcRepository = jdbcRepository;
		this.passageJdbcRepository = passageJdbcRepository;
	}

	@Override
	public Exercise save(Exercise exercise) {
		ExerciseEntity saved = jdbcRepository.save(ExerciseMapper.toEntity(exercise));
		return ExerciseMapper.toDomain(saved);
	}

	@Override
	public List<Exercise> findByTrainingId(long trainingId) {
		return jdbcRepository.findByTrainingId(trainingId).stream().map(ExerciseMapper::toDomain).toList();
	}

	@Override
	public Optional<Exercise> findById(long id) {
		return jdbcRepository.findById(id).map(ExerciseMapper::toDomain);
	}

	@Override
	public List<ExercisePassage> findPassagesByExerciseId(long exerciseId) {
		return passageJdbcRepository.findByExerciseIdOrderByFromSecondsAsc(exerciseId)
			.stream()
			.map(ExerciseMapper::toDomain)
			.toList();
	}

	@Override
	@Transactional
	public ExercisePassage addPassage(long exerciseId, ExercisePassage passage) {
		ExercisePassageEntity saved = passageJdbcRepository.save(ExerciseMapper.toEntity(passage, exerciseId));
		return ExerciseMapper.toDomain(saved);
	}

	@Override
	@Transactional
	public void deletePassage(long exerciseId, long passageId) {
		ExercisePassageEntity entity = passageJdbcRepository.findById(passageId)
			.filter(p -> p.exerciseId() != null && p.exerciseId() == exerciseId)
			.orElseThrow(() -> new NoSuchElementException(
					"ExercisePassage " + passageId + " nao encontrado no exercicio " + exerciseId));
		passageJdbcRepository.delete(entity);
	}
}
