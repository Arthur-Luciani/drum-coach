package dev.drumcoach.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Repository;

import dev.drumcoach.application.GoalRepository;
import dev.drumcoach.domain.Goal;

/** Implementacao do port {@link GoalRepository} via Spring Data JDBC. */
@Repository
public class GoalRepositoryImpl implements GoalRepository {

	private final SpringDataGoalRepository jdbcRepository;

	public GoalRepositoryImpl(SpringDataGoalRepository jdbcRepository) {
		this.jdbcRepository = jdbcRepository;
	}

	@Override
	public Goal save(Goal goal) {
		GoalEntity saved = jdbcRepository.save(GoalMapper.toEntity(goal));
		return GoalMapper.toDomain(saved);
	}

	@Override
	public List<Goal> findAll() {
		return StreamSupport.stream(jdbcRepository.findAll().spliterator(), false).map(GoalMapper::toDomain).toList();
	}

	@Override
	public Optional<Goal> findById(long id) {
		return jdbcRepository.findById(id).map(GoalMapper::toDomain);
	}
}
