package dev.drumcoach.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.application.ExecutionRepository;
import dev.drumcoach.domain.Execution;
import dev.drumcoach.domain.ExecutionExerciseLog;

/**
 * Implementacao do port {@link ExecutionRepository} via Spring Data JDBC. {@code
 * ExecutionEntity} e {@code ExecutionExerciseLogEntity} sao tabelas flat separadas (nao
 * um agregado Spring Data JDBC nativo via {@code @MappedCollection}) - esta classe
 * orquestra explicitamente a raiz + os filhos numa unica transacao, o que da o mesmo
 * resultado pratico (persistencia atomica execution + logs) com um mapeamento mais
 * simples e previsivel.
 */
@Repository
public class ExecutionRepositoryImpl implements ExecutionRepository {

	private final SpringDataExecutionRepository executionJdbcRepository;
	private final SpringDataExecutionExerciseLogRepository logJdbcRepository;

	public ExecutionRepositoryImpl(SpringDataExecutionRepository executionJdbcRepository,
			SpringDataExecutionExerciseLogRepository logJdbcRepository) {
		this.executionJdbcRepository = executionJdbcRepository;
		this.logJdbcRepository = logJdbcRepository;
	}

	@Override
	@Transactional
	public Execution save(Execution execution) {
		ExecutionEntity savedRoot = executionJdbcRepository.save(ExecutionMapper.toEntity(execution));
		List<ExecutionExerciseLog> savedLogs = execution.logs()
			.stream()
			.map(log -> ExecutionMapper.toEntity(log, savedRoot.id()))
			.map(logJdbcRepository::save)
			.map(ExecutionMapper::toDomain)
			.toList();
		return ExecutionMapper.toDomain(savedRoot, savedLogs);
	}

	@Override
	public List<Execution> findAll() {
		return toDomainWithLogs(executionJdbcRepository.findAll());
	}

	@Override
	public List<Execution> findByTrainingId(long trainingId) {
		return executionJdbcRepository.findByTrainingId(trainingId).stream().map(this::toDomainWithLogs).toList();
	}

	@Override
	public List<Execution> findByGoalId(long goalId) {
		return executionJdbcRepository.findByGoalId(goalId).stream().map(this::toDomainWithLogs).toList();
	}

	@Override
	public Optional<Execution> findById(long id) {
		return executionJdbcRepository.findById(id).map(this::toDomainWithLogs);
	}

	@Override
	public long countByTrainingId(long trainingId) {
		return executionJdbcRepository.countByTrainingId(trainingId);
	}

	@Override
	public long countExerciseLogsByExerciseId(long exerciseId) {
		return logJdbcRepository.countByExerciseId(exerciseId);
	}

	private List<Execution> toDomainWithLogs(Iterable<ExecutionEntity> entities) {
		return StreamSupport.stream(entities.spliterator(), false).map(this::toDomainWithLogs).toList();
	}

	private Execution toDomainWithLogs(ExecutionEntity entity) {
		List<ExecutionExerciseLog> logs = logJdbcRepository.findByExecutionId(entity.id())
			.stream()
			.map(ExecutionMapper::toDomain)
			.toList();
		return ExecutionMapper.toDomain(entity, logs);
	}
}
