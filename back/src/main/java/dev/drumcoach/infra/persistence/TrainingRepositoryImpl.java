package dev.drumcoach.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Repository;

import dev.drumcoach.application.TrainingRepository;
import dev.drumcoach.domain.Training;

/** Implementacao do port {@link TrainingRepository} via Spring Data JDBC. */
@Repository
public class TrainingRepositoryImpl implements TrainingRepository {

	private final SpringDataTrainingRepository jdbcRepository;

	public TrainingRepositoryImpl(SpringDataTrainingRepository jdbcRepository) {
		this.jdbcRepository = jdbcRepository;
	}

	@Override
	public Training save(Training training) {
		TrainingEntity saved = jdbcRepository.save(TrainingMapper.toEntity(training));
		return TrainingMapper.toDomain(saved);
	}

	@Override
	public List<Training> findAll() {
		return StreamSupport.stream(jdbcRepository.findAll().spliterator(), false).map(TrainingMapper::toDomain).toList();
	}

	@Override
	public List<Training> findByGoalId(long goalId) {
		return jdbcRepository.findByGoalId(goalId).stream().map(TrainingMapper::toDomain).toList();
	}

	@Override
	public Optional<Training> findById(long id) {
		return jdbcRepository.findById(id).map(TrainingMapper::toDomain);
	}
}
